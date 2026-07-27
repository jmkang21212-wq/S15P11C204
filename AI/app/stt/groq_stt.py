"""발화를 Groq Whisper로 전사한다.

큐 + 워커 스레드 구조다. submit()은 절대 블로킹하지 않는다.
오디오를 읽는 쪽이 STT 응답을 기다리면 그 사이 마이크 입력이 밀리기 때문이다.

결과는 도착 순서대로 나오며, 발화 순서와 다를 수 있다(워커가 여러 개라서).
Transcript가 start_ts를 들고 있으니 필요한 쪽에서 정렬하면 된다.
"""

import io
import logging
import queue
import re
import threading
import time
import wave
from dataclasses import dataclass
from typing import Iterator, Optional

import numpy as np
from groq import APIConnectionError, APIStatusError, Groq, RateLimitError

from app.audio.segmenter import Utterance
from app.config import (
    AVG_LOGPROB_MIN,
    GROQ_API_KEY,
    HALLUCINATION_NS_MIN,
    NO_SPEECH_MAX,
    SAMPLE_RATE,
    STT_FILTER_ENABLED,
    STT_LANGUAGE,
    STT_MAX_RETRIES,
    STT_MODEL,
    STT_QUEUE_MAX,
    STT_WORKERS,
)

log = logging.getLogger(__name__)

_SENTINEL = object()


@dataclass
class Transcript:
    seq: int
    text: str
    start_ts: float
    end_ts: float
    latency: float          # STT 왕복에 걸린 시간
    no_speech_prob: float
    avg_logprob: float
    forced: bool            # 최대 길이에 걸려 잘린 발화인지
    session_id: str = ""

    @property
    def duration(self) -> float:
        return self.end_ts - self.start_ts


def pcm_to_wav(samples: np.ndarray, sample_rate: int = SAMPLE_RATE) -> bytes:
    """float32[-1,1] -> 16bit PCM WAV 바이트. Groq는 파일 업로드 방식이라 필요하다."""
    pcm16 = (np.clip(samples, -1.0, 1.0) * 32767).astype(np.int16)
    buf = io.BytesIO()
    with wave.open(buf, "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(sample_rate)
        wf.writeframes(pcm16.tobytes())
    return buf.getvalue()


class GroqSTT:
    def __init__(
        self,
        workers: int = STT_WORKERS,
        maxsize: int = STT_QUEUE_MAX,
        max_retries: int = STT_MAX_RETRIES,
        model: str = STT_MODEL,
        language: str = STT_LANGUAGE,
    ):
        self.model = model
        self.language = language
        self.max_retries = max_retries

        self._client = Groq(api_key=GROQ_API_KEY)
        self._in: queue.Queue = queue.Queue(maxsize=maxsize)
        self._out: queue.Queue = queue.Queue()
        self._threads = [
            threading.Thread(target=self._worker, name=f"stt-{i}", daemon=True)
            for i in range(workers)
        ]
        self._stopping = threading.Event()

        # 통계. 조용히 실패하는 게 제일 무서우므로 전부 센다.
        self.submitted = 0
        self.completed = 0
        self.dropped = 0
        self.failed = 0
        self.filtered = 0

    def start(self) -> "GroqSTT":
        for t in self._threads:
            t.start()
        return self

    def submit(self, utt: Utterance) -> bool:
        """전사 대기열에 넣는다. 큐가 꽉 차면 가장 오래된 것을 버린다."""
        self.submitted += 1
        try:
            self._in.put_nowait(utt)
            return True
        except queue.Full:
            try:
                self._in.get_nowait()  # 오래된 것부터 버려야 최신 발화가 빨리 처리된다
                self.dropped += 1
                self._in.put_nowait(utt)
                return True
            except (queue.Empty, queue.Full):
                self.dropped += 1
                return False

    def results(self) -> Iterator[Transcript]:
        """완료된 전사를 도착 순서대로 흘려보낸다. stop() 전까지 블로킹."""
        while True:
            item = self._out.get()
            if item is _SENTINEL:
                return
            yield item

    def stop(self, timeout: float = 5.0) -> None:
        self._stopping.set()
        for t in self._threads:
            t.join(timeout=timeout)
        self._out.put(_SENTINEL)

    # --- 내부 ---

    def _worker(self) -> None:
        while not self._stopping.is_set():
            try:
                utt = self._in.get(timeout=0.2)
            except queue.Empty:
                continue
            try:
                tr = self._transcribe(utt)
            except Exception:
                # 워커가 죽으면 큐만 조용히 쌓이다가 시스템이 무증상으로 멈춘다.
                # 어떤 예외가 와도 스레드는 살려둔다.
                self.failed += 1
                log.exception("전사 실패 seq=%s", utt.seq)
                continue
            if tr is not None:
                self.completed += 1
                self._out.put(tr)

    def _transcribe(self, utt: Utterance) -> Optional[Transcript]:
        wav = pcm_to_wav(utt.pcm)
        started = time.perf_counter()
        resp = self._call_with_retry(wav, utt.seq)
        latency = time.perf_counter() - started
        if resp is None:
            return None

        text = (getattr(resp, "text", "") or "").strip()
        no_speech, logprob = _segment_stats(resp)

        if STT_FILTER_ENABLED and _is_hallucination(text, no_speech, logprob):
            self.filtered += 1
            # 조용히 버리면 진짜 위험 발언이 사라져도 모른다. 항상 보이게 남긴다.
            log.info("환각으로 버림 seq=%s ns=%.2f lp=%+.2f text=%r",
                     utt.seq, no_speech, logprob, text)
            return None
        if not text:
            return None

        return Transcript(
            seq=utt.seq,
            text=text,
            start_ts=utt.start_ts,
            end_ts=utt.end_ts,
            latency=latency,
            no_speech_prob=no_speech,
            avg_logprob=logprob,
            forced=utt.forced,
            session_id=utt.session_id,
        )

    def _call_with_retry(self, wav: bytes, seq: int):
        for attempt in range(self.max_retries + 1):
            try:
                return self._client.audio.transcriptions.create(
                    file=(f"utt-{seq}.wav", wav),
                    model=self.model,
                    language=self.language,
                    response_format="verbose_json",  # no_speech_prob가 여기서만 나온다
                )
            except RateLimitError as e:
                wait = _retry_after(e, default=2.0 * (attempt + 1))
                log.warning("레이트리밋 seq=%s %.1f초 대기 (%d/%d)",
                            seq, wait, attempt + 1, self.max_retries)
                if attempt == self.max_retries:
                    break
                time.sleep(wait)
            except (APIConnectionError, APIStatusError) as e:
                if attempt == self.max_retries:
                    log.error("전사 포기 seq=%s: %s", seq, e)
                    break
                time.sleep(0.5 * (attempt + 1))
        self.failed += 1
        return None


def _retry_after(exc, default: float) -> float:
    """429 응답의 retry-after를 존중한다. 없으면 기본값."""
    resp = getattr(exc, "response", None)
    headers = getattr(resp, "headers", None) or {}
    raw = headers.get("retry-after") or headers.get("Retry-After")
    try:
        return min(float(raw), 30.0)
    except (TypeError, ValueError):
        return default


def _segment_stats(resp) -> tuple[float, float]:
    """verbose_json의 세그먼트들에서 무음 확률과 신뢰도를 뽑아 평균낸다."""
    segments = getattr(resp, "segments", None) or []
    no_speech, logprob = [], []
    for seg in segments:
        get = seg.get if isinstance(seg, dict) else lambda k, d=None: getattr(seg, k, d)
        if (v := get("no_speech_prob")) is not None:
            no_speech.append(float(v))
        if (v := get("avg_logprob")) is not None:
            logprob.append(float(v))
    # 값이 없으면 '정상'으로 간주한다. 필터가 없다고 멀쩡한 발화를 버리면 안 된다.
    return (
        sum(no_speech) / len(no_speech) if no_speech else 0.0,
        sum(logprob) / len(logprob) if logprob else 0.0,
    )


# Whisper가 무음에 습관적으로 뱉는 문구들. 유튜브 자막으로 학습된 흔적이다.
# 실측에서 무음/잡음 4종이 전부 "감사합니다"로 나왔다.
_HALLUCINATION_PHRASES = {
    "감사합니다",
    "감사합니다다음영상에서만나요",
    "시청해주셔서감사합니다",
    "오늘도시청해주셔서감사합니다",
    "이번영상도시청해주셔서감사합니다",
    "지금까지시청해주셔서감사합니다",
    "다음영상에서만나요",
    "구독과좋아요부탁드립니다",
    "구독좋아요알림설정",
    "구독과좋아요알림설정부탁드립니다",
    "자막제공",
    "한글자막",
    "영상편집",
    "끝까지시청해주셔서감사합니다",
}

_STRIP = re.compile(r"[^0-9a-z가-힣ㄱ-ㆎ]")


def _is_hallucination(text: str, no_speech: float, logprob: float) -> bool:
    if not text:
        return True

    # 알려진 환각 문구가 '문장 전체'일 때만 건다. 부분 일치로 걸면
    # "도와주셔서 정말 감사합니다" 같은 멀쩡한 발화가 날아간다.
    bare = _STRIP.sub("", text.lower())
    if bare in _HALLUCINATION_PHRASES and no_speech > HALLUCINATION_NS_MIN:
        return True

    # 아래 둘은 각각 독립 조건이다. AND로 묶으면 안 된다.
    # 환각은 신뢰도가 높은 채로 나오기 때문에 둘 다 요구하면 영영 안 걸린다.
    if no_speech > NO_SPEECH_MAX:
        return True
    return logprob < AVG_LOGPROB_MIN
