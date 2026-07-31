import asyncio
import json
import logging
import threading
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Optional

import numpy as np
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.responses import FileResponse

from app.audio.segmenter import VadSegmenter
from app.config import CHUNK, LOG_FILE, PROMISE_ENABLED, SAMPLE_RATE
from app.detect.dictionary import DictionaryMatcher
from app.detect.llm_judge import LLMJudge, RollingContext
from app.detect.promise import PromiseExtractor
from app.logging_setup import setup_logging
from app.sink.spring import SpringSink
from app.stt.groq_stt import GroqSTT

# uvicorn은 root 로거를 설정하지 않는다 — 이게 없으면 아래 log.info가 전부 사라진다
log = setup_logging(__name__, LOG_FILE)

_matcher = DictionaryMatcher()
_stt: Optional[GroqSTT] = None
_judge: Optional[LLMJudge] = None
_promise: Optional[PromiseExtractor] = None
_spring: Optional[SpringSink] = None
_registry: "SessionRegistry"
_loop: Optional[asyncio.AbstractEventLoop] = None

STATIC_DIR = Path(__file__).resolve().parent.parent / "static"


class Session:
    def __init__(self, session_id: str, websocket: WebSocket,
                 send_transcripts: bool = False):
        self.id = session_id
        self.ws = websocket
        # 전사는 기본으로 안 보낸다. 경고에 text가 이미 들어있어 화면 표시에는
        # 필요 없고, 저장은 서버가 Spring으로 직접 하기 때문이다.
        # 개발/디버깅용으로만 켠다.
        self.send_transcripts = send_transcripts
        self.segmenter = VadSegmenter(session_id=session_id)
        self.context = RollingContext()
        self.outbound: asyncio.Queue = asyncio.Queue()
        self._residual = np.empty(0, dtype=np.float32)

    def feed_bytes(self, data: bytes) -> list:
        if len(data) % 2:
            data = data[:-1]  # 홀수 바이트면 잘린 샘플이다. 버린다.
        pcm = np.frombuffer(data, dtype="<i2").astype(np.float32) / 32768.0
        buf = np.concatenate((self._residual, pcm)) if self._residual.size else pcm

        utterances = []
        offset = 0
        while len(buf) - offset >= CHUNK:
            utt = self.segmenter.feed(buf[offset:offset + CHUNK])
            offset += CHUNK
            if utt is not None:
                utterances.append(utt)
        self._residual = buf[offset:].copy()
        return utterances

    def push(self, event: dict) -> None:
        if _loop is None:
            return
        _loop.call_soon_threadsafe(self.outbound.put_nowait, event)


class SessionRegistry:
    def __init__(self):
        self._sessions: dict[str, Session] = {}
        self._lock = threading.Lock()

    def add(self, session: Session) -> None:
        with self._lock:
            self._sessions[session.id] = session

    def remove(self, session_id: str) -> None:
        with self._lock:
            self._sessions.pop(session_id, None)

    def get(self, session_id: str) -> Optional[Session]:
        with self._lock:
            return self._sessions.get(session_id)


_registry = SessionRegistry()


def verify_handshake(session_id: str, token: str) -> bool:
    """접속 자격 확인.

    TODO(Phase 6): Spring이 발급한 JWT를 여기서 검증한다.
    서명 검증에 필요한 공개키/알고리즘이 정해지면 교체할 것.
    지금은 session_id가 있는지만 본다.
    """
    return bool(session_id)


def _dispatch_transcripts() -> None:
    """STT 결과를 받아 Spring에 기록하고, 사전매칭 결과를 세션으로 되돌린다.

    Spring 전송은 세션이 남아있는지와 무관하게 한다. 연결이 끊기는 순간
    처리 중이던 발화까지 기록돼야 리포트에 구멍이 안 생긴다.
    """
    for tr in _stt.results():
        session = _registry.get(tr.session_id)  # 이미 끊겼으면 None

        if _spring is not None:
            _spring.send_transcript(tr)

        if session is not None:
            session.context.add(tr)
            if session.send_transcripts:
                session.push({
                    "type": "transcript",
                    "seq": tr.seq,
                    "text": tr.text,
                    "start_ts": tr.start_ts,
                    "end_ts": tr.end_ts,
                    "forced": tr.forced,
                })

        hits = _matcher.match(tr.text)
        if hits:
            if _spring is not None:
                _spring.send_dictionary_alert(tr, hits)
            if session is not None:
                # 한 발화에 여러 단어가 걸려도 경고는 하나로 묶는다.
                # 따로 보내면 같은 문장이 화면에 여러 번 뜬다.
                worst = max(hits, key=lambda h: h.rank)
                session.push({
                    "type": "alert",
                    "source": "dictionary",
                    "seq": tr.seq,
                    "severity": worst.severity,
                    "category": worst.category,
                    "text": tr.text,
                    "start_ts": tr.start_ts,
                    "end_ts": tr.end_ts,
                    "hits": [
                        {"matched": h.matched, "severity": h.severity,
                         "category": h.category, "start": h.start, "end": h.end}
                        for h in hits
                    ],
                })
        elif _judge is not None and session is not None:
            # 사전에 걸린 건 결론이 났다. 회색지대만 LLM으로 넘긴다.
            # 세션이 없으면 문맥을 만들 수 없어 판정을 건너뛴다.
            _judge.submit(tr, context=session.context.snapshot(before_ts=tr.start_ts))

        # 약속 추적은 위험 판정과 독립이다. 욕설이 섞인 발화에도 약속이 있을 수 있고,
        # 반대로 멀쩡한 발화 대부분은 약속이 아니다. 게이트가 안에서 걸러낸다.
        if _promise is not None and session is not None:
            _promise.submit(tr, context=session.context.snapshot(before_ts=tr.start_ts))


def _dispatch_verdicts() -> None:
    for v in _judge.results():
        if not v.is_risky:
            continue

        if _spring is not None:
            _spring.send_llm_alert(v)

        session = _registry.get(v.session_id)
        if session is None:
            continue
        session.push({
            "type": "alert",
            "source": "llm",
            "seq": v.seq,
            # 사전 경고와 필드명을 맞춘다. 프론트가 source별로 분기하지 않게.
            "severity": v.risk,
            "category": v.category,
            "reason": v.reason,
            "text": v.text,
            "start_ts": v.start_ts,
            "end_ts": v.end_ts,
        })


def _dispatch_promises() -> None:
    for p in _promise.results():
        if _spring is not None:
            _spring.send_promise(p)

        session = _registry.get(p.session_id)
        if session is None:
            continue
        session.push({
            "type": "promise",
            "seq": p.seq,
            "content": p.content,
            "text": p.raw_text,
            "deadline_text": p.deadline_text,
            "deadline_at": p.deadline_at,
            "condition": p.condition,
            "confidence": round(p.confidence, 3),
            "spoken_at": p.spoken_at,
        })


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _stt, _judge, _promise, _loop, _spring
    _loop = asyncio.get_running_loop()
    _spring = SpringSink().start()
    _stt = GroqSTT().start()
    threading.Thread(target=_dispatch_transcripts, daemon=True,
                     name="dispatch-stt").start()
    try:
        _judge = LLMJudge().start()
        threading.Thread(target=_dispatch_verdicts, daemon=True,
                         name="dispatch-llm").start()
    except RuntimeError as e:
        log.warning("LLM 판정 비활성화: %s", e)
    if PROMISE_ENABLED:
        try:
            _promise = PromiseExtractor().start()
            threading.Thread(target=_dispatch_promises, daemon=True,
                             name="dispatch-promise").start()
        except RuntimeError as e:
            log.warning("약속 추적 비활성화: %s", e)
    log.info("준비 완료 (사전 %d개 표기)", _matcher.size)
    yield
    if _stt is not None:
        _stt.stop()
    if _judge is not None:
        _judge.stop()
    if _promise is not None:
        _promise.stop()
    if _spring is not None:
        _spring.stop()  # 배치에 남은 전사까지 마저 보낸다


app = FastAPI(title="LAIVE 실시간 위험 발언 감지", lifespan=lifespan)


@app.get("/health")
async def health() -> dict:
    return {"status": "ok", "dictionary": _matcher.size,
            "llm": _judge is not None,
            "promise": _promise is not None,
            "spring": _spring is not None and _spring.enabled}


@app.get("/")
async def client_page():
    return FileResponse(STATIC_DIR / "client.html")


@app.websocket("/ws/audio")
async def ws_audio(websocket: WebSocket) -> None:
    await websocket.accept()

    # 첫 메시지는 반드시 핸드셰이크 JSON이다.
    try:
        raw = await asyncio.wait_for(websocket.receive_text(), timeout=10.0)
        handshake = json.loads(raw)
    except (asyncio.TimeoutError, json.JSONDecodeError, WebSocketDisconnect):
        await websocket.close(code=1008, reason="handshake required")
        return

    session_id = str(handshake.get("session_id", "")).strip()
    if not verify_handshake(session_id, str(handshake.get("token", ""))):
        await websocket.close(code=1008, reason="unauthorized")
        return

    session = Session(session_id, websocket,
                      send_transcripts=bool(handshake.get("transcripts", False)))
    _registry.add(session)

    # Spring 저장에는 broadcast_sessions.id가 필요하다. session_id는 프론트가 탭 충돌을
    # 피하려고 만든 임의 문자열이라 그 역할을 못 한다. 프론트가 방송 시작 응답으로 이미
    # 갖고 있는 번호를 핸드셰이크에 실어 보내면 여기서 묶는다. 없으면 감지와 프론트
    # 전송은 그대로 돌고 Spring 저장만 건너뛴다.
    broadcast_session_id = _parse_broadcast_session_id(handshake.get("broadcastSessionId"))
    if _spring is not None and _spring.enabled:
        if broadcast_session_id is None:
            log.warning("broadcastSessionId가 없어 Spring 저장을 건너뜁니다: session_id=%s",
                        session_id)
        else:
            _spring.bind_session(session_id, broadcast_session_id)

    await websocket.send_json({
        "type": "ready", "session_id": session_id,
        "sample_rate": SAMPLE_RATE, "chunk": CHUNK,
    })
    log.info("세션 접속 %s (broadcastSessionId=%s)", session_id, broadcast_session_id)

    sender = asyncio.create_task(_send_loop(session))
    try:
        while True:
            data = await websocket.receive_bytes()
            # VAD 추론을 이벤트 루프에서 돌리면 세션이 늘수록 전체가 느려진다.
            utterances = await asyncio.to_thread(session.feed_bytes, data)
            for utt in utterances:
                _stt.submit(utt)
    except (WebSocketDisconnect, RuntimeError):
        pass
    finally:
        last = session.segmenter.close()
        if last is not None and _stt is not None:
            _stt.submit(last)
        sender.cancel()
        _registry.remove(session_id)
        if _promise is not None:
            _promise.forget(session_id)
        # 전사 배치가 아직 큐에 남아 있을 수 있어 바로 풀지 않는다.
        # 남은 것을 다 보낸 뒤 정리한다.
        if _spring is not None and _spring.enabled:
            _spring.release_session_later(session_id)
        log.info("세션 종료 %s", session_id)


def _parse_broadcast_session_id(value: object) -> int | None:
    """핸드셰이크의 broadcastSessionId를 읽는다. 없거나 이상하면 None."""
    if value is None:
        return None
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        log.warning("broadcastSessionId를 숫자로 읽을 수 없습니다: %r", value)
        return None
    return parsed if parsed > 0 else None


async def _send_loop(session: Session) -> None:
    try:
        while True:
            event = await session.outbound.get()
            await session.ws.send_json(event)
    except (asyncio.CancelledError, WebSocketDisconnect, RuntimeError):
        pass
