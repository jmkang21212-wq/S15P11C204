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
from app.auth import Ticket, TicketVerifier
from app.config import CHUNK, LOG_FILE, PROMISE_ENABLED, SAMPLE_RATE
from app.detect.dictionary import BASE as _matcher, matcher_for
from app.detect.llm_judge import LLMJudge, RollingContext
from app.detect.personal import EMPTY, PersonalTerms
from app.detect.promise import PromiseExtractor
from app.detect.terms_subscriber import TermsSubscriber
from app.logging_setup import setup_logging
from app.sink.spring import SpringSink
from app.stt.groq_stt import GroqSTT

# uvicorn은 root 로거를 설정하지 않는다 — 이게 없으면 아래 log.info가 전부 사라진다
log = setup_logging(__name__, LOG_FILE)

# 기본 사전. 개인 사전이 없는 세션과, 세션이 이미 끊긴 뒤 도착한 전사가 쓴다.
_stt: Optional[GroqSTT] = None
_judge: Optional[LLMJudge] = None
_promise: Optional[PromiseExtractor] = None
_spring: Optional[SpringSink] = None
_registry: "SessionRegistry"
_loop: Optional[asyncio.AbstractEventLoop] = None
_ticket_verifier: Optional[TicketVerifier] = None
_terms_subscriber: Optional[TermsSubscriber] = None

STATIC_DIR = Path(__file__).resolve().parent.parent / "static"


class Session:
    def __init__(self, session_id: str, websocket: WebSocket,
                 send_transcripts: bool = False,
                 streamer_id: int = 0, terms: PersonalTerms = EMPTY):
        self.id = session_id
        self.ws = websocket
        # 전사는 기본으로 안 보낸다. 경고에 text가 이미 들어있어 화면 표시에는
        # 필요 없고, 저장은 서버가 Spring으로 직접 하기 때문이다.
        # 개발/디버깅용으로만 켠다.
        self.send_transcripts = send_transcripts
        # pub/sub이 방송 중 사전 변경을 이 세션에 밀어 넣을 때 대상을 찾는 키다.
        self.streamer_id = streamer_id
        self.segmenter = VadSegmenter(session_id=session_id)
        self.context = RollingContext()
        self.outbound: asyncio.Queue = asyncio.Queue()
        self._residual = np.empty(0, dtype=np.float32)
        self.apply_terms(terms)

    def apply_terms(self, terms: PersonalTerms) -> None:
        """개인 사전을 적용한다. 접속 시 한 번, 이후 pub/sub 수신마다 호출된다.

        matcher와 allow 목록을 통째로 갈아끼운다. automaton을 제자리에서 고치면
        판정 스레드가 반쯤 바뀐 사전으로 매칭하게 된다. 참조 대입은 GIL 아래에서
        원자적이라 락 없이 안전하다.
        """
        self.terms = terms
        self.matcher = matcher_for(terms)
        self.allow_display = terms.allow_display

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

    def by_streamer(self, streamer_id: int) -> list[Session]:
        """한 스트리머의 살아있는 세션들. 사전 변경을 밀어 넣을 때 쓴다.

        보통 하나지만 미니 모드 창처럼 같은 방송에 두 번 붙는 경우가 있어 목록으로 준다.
        """
        with self._lock:
            return [s for s in self._sessions.values() if s.streamer_id == streamer_id]


_registry = SessionRegistry()


def verify_handshake(handshake: dict) -> Optional[Ticket]:
    """접속 자격을 확인하고 **신뢰할 수 있는** 티켓을 돌려준다.

    반환값이 None이면 접속을 거절해야 한다.

    세션 번호뿐 아니라 개인 사전도 여기서 나온다. 핸드셰이크로 받으면 조작된 허용어로
    감지를 무력화할 수 있다 (RISK_POLICY_SPEC.md §3).

    핵심: 세션 번호를 핸드셰이크가 아니라 **티켓에서** 얻는다. 예전에는 클라이언트가
    보낸 broadcastSessionId를 그대로 믿어서, 아무 번호나 넣으면 남의 방송에 전사·
    위험 이벤트를 기록할 수 있었다. Spring이 소유권을 확인한 뒤에만 티켓을 발급하므로
    그 경로가 막힌다.

    개발 우회(VOICE_AUTH_DEV_BYPASS=1)일 때만 예전처럼 핸드셰이크 값을 받아준다.
    """
    ticket_value = str(handshake.get("ticket", "")).strip()
    ticket = _ticket_verifier.consume(ticket_value) if ticket_value else None
    if ticket is not None:
        return ticket

    if _ticket_verifier.dev_bypass:
        log.warning("티켓 없이 접속 허용(개발 우회) — 배포 환경이면 즉시 끌 것")
        session_id = _parse_broadcast_session_id(handshake.get("broadcastSessionId"))
        if session_id is None:
            return None
        # 개발 우회에는 개인 사전이 없다. 스트리머를 특정할 방법이 없기 때문이다.
        return Ticket(streamer_id=0, broadcast_session_id=session_id, terms=EMPTY)
    return None


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

        # 세션이 이미 끊겼으면 개인 사전을 알 수 없다. 기본 사전으로 떨어뜨린다 —
        # 여기서 멈추면 마지막 발화들이 Spring에 기록되지 않아 리포트에 구멍이 생긴다.
        matcher = session.matcher if session is not None else _matcher

        hits = matcher.match(tr.text)
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
            #
            # 허용어를 함께 넘긴다. 사전에서 빼는 것만으로는 부족하다 — 빠진 발화는
            # 바로 이 경로로 와서 LLM에게 다시 걸린다 (RISK_POLICY_SPEC.md §2).
            _judge.submit(tr, context=session.context.snapshot(before_ts=tr.start_ts),
                          allow_terms=session.allow_display)

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
        # 다른 전송과 달리 응답을 기다린다. 저장된 약속의 id를 프론트에 같이
        # 실어보내야 하는데, 그 id는 POST 응답에만 있기 때문이다. 기다리는 동안
        # 막히는 것은 이 스레드뿐이라 감지와 오디오는 영향이 없다.
        promise_id = _spring.send_promise(p) if _spring is not None else None

        session = _registry.get(p.session_id)
        if session is None:
            continue
        session.push({
            "type": "promise",
            "seq": p.seq,
            # PROMISES의 행 id. 프론트는 이 값으로 확정/무시를 호출한다.
            # Spring 저장이 꺼져 있거나 실패했으면 null이고, 그때는 카드만 뜨고
            # 확정/무시 버튼은 쓸 수 없다.
            "promise_id": promise_id,
            "content": p.content,
            "text": p.raw_text,
            "deadline_text": p.deadline_text,
            "deadline_at": p.deadline_at,
            "condition": p.condition,
            "confidence": round(p.confidence, 3),
            "spoken_at": p.spoken_at,
        })


def _apply_terms(streamer_id: int, terms: PersonalTerms) -> None:
    """pub/sub으로 받은 사전을 그 스트리머의 살아있는 세션들에 적용한다.

    구독 스레드에서 호출된다. 판정 스레드와 겹치지만 apply_terms가 참조를 통째로
    갈아끼우므로 락이 필요 없다.
    """
    sessions = _registry.by_streamer(streamer_id)
    for session in sessions:
        session.apply_terms(terms)
    if sessions:
        log.info("개인 사전 갱신 streamerId=%s 세션 %d개 (금칙어 %d·허용어 %d)",
                 streamer_id, len(sessions), len(terms.block), len(terms.allow))


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _stt, _judge, _promise, _loop, _spring, _ticket_verifier, _terms_subscriber
    _loop = asyncio.get_running_loop()
    _ticket_verifier = TicketVerifier()
    _terms_subscriber = TermsSubscriber(_apply_terms).start()
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
    if _terms_subscriber is not None:
        _terms_subscriber.stop()
    if _spring is not None:
        _spring.stop()  # 배치에 남은 전사까지 마저 보낸다


app = FastAPI(title="LAIVE 실시간 위험 발언 감지", lifespan=lifespan)


@app.get("/health")
async def health() -> dict:
    return {"status": "ok", "dictionary": _matcher.size,
            "llm": _judge is not None,
            "promise": _promise is not None,
            "spring": _spring is not None and _spring.enabled,
            # 개발 우회가 켜져 있으면 헬스체크에 드러낸다 — 배포 환경에서
            # 켜진 채 방치되는 것을 눈으로 잡을 수 있어야 한다
            "authBypass": _ticket_verifier is not None and _ticket_verifier.dev_bypass}


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
    if not session_id:
        await websocket.close(code=1008, reason="session_id required")
        return

    # 티켓 검증 — 실패하면 접속 자체를 막는다. 통과하면 신뢰할 수 있는 세션 번호를 얻는다.
    # session_id는 프론트가 탭 충돌을 피하려고 만든 임의 문자열이라 Spring 저장에 못 쓴다.
    ticket = verify_handshake(handshake)
    if ticket is None:
        log.warning("접속 거절 session_id=%s — 티켓이 없거나 만료·재사용됐다", session_id)
        await websocket.close(code=1008, reason="unauthorized")
        return
    broadcast_session_id = ticket.broadcast_session_id

    session = Session(session_id, websocket,
                      send_transcripts=bool(handshake.get("transcripts", False)),
                      streamer_id=ticket.streamer_id, terms=ticket.terms)
    _registry.add(session)

    if _spring is not None and _spring.enabled:
        _spring.bind_session(session_id, broadcast_session_id)

    await websocket.send_json({
        "type": "ready", "session_id": session_id,
        "sample_rate": SAMPLE_RATE, "chunk": CHUNK,
    })
    log.info("세션 접속 %s (broadcastSessionId=%s, 개인사전 금칙어 %d·허용어 %d)",
             session_id, broadcast_session_id,
             len(ticket.terms.block), len(ticket.terms.allow))

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
