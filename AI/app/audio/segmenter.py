"""마이크든 WebSocket이든, 오디오 청크를 받아 '발화' 단위로 잘라내는 모듈.

입력 소스를 전혀 모른다는 게 핵심이다. 지금은 로컬 마이크가 청크를 넣어주고,
나중에는 WebSocket이 넣어주지만 이 파일은 바뀌지 않는다.
"""

import collections
import math
import time
from dataclasses import dataclass
from typing import Optional

import numpy as np
import torch
from silero_vad import load_silero_vad

from app.config import (
    CHUNK,
    MAX_UTTERANCE_MS,
    MIN_SPEECH_MS,
    PRE_ROLL_MS,
    SAMPLE_RATE,
    SILENCE_MS,
    VAD_THRESHOLD,
)


@dataclass
class Utterance:
    """잘라낸 발화 하나."""

    pcm: np.ndarray      # float32 [-1, 1]. WAV 인코딩은 STT 단계의 일이다.
    start_ts: float      # epoch 초. 리포트에서 시각 범위로 다시 찾으려면 반드시 필요하다.
    end_ts: float
    seq: int             # 스트림 내 순번
    forced: bool         # 무음이 아니라 최대 길이에 걸려 끊긴 것인지
    session_id: str = ""  # 여러 명이 붙었을 때 결과를 제 소켓으로 되돌리기 위한 꼬리표

    @property
    def duration(self) -> float:
        return self.end_ts - self.start_ts


class VadSegmenter:
    """청크를 하나씩 받아, 발화가 완성되는 순간 Utterance를 돌려준다.

    호출 예:
        seg = VadSegmenter()
        for chunk in source:
            utt = seg.feed(chunk)
            if utt:
                ...
    """

    def __init__(
        self,
        sample_rate: int = SAMPLE_RATE,
        chunk: int = CHUNK,
        threshold: float = VAD_THRESHOLD,
        silence_ms: int = SILENCE_MS,
        min_speech_ms: int = MIN_SPEECH_MS,
        max_utterance_ms: int = MAX_UTTERANCE_MS,
        pre_roll_ms: int = PRE_ROLL_MS,
        session_id: str = "",
    ):
        self.sample_rate = sample_rate
        self.threshold = threshold
        self.session_id = session_id

        chunk_ms = chunk * 1000 / sample_rate  # 16kHz / 512샘플 = 32ms
        self._silence_limit = math.ceil(silence_ms / chunk_ms)
        self._min_speech = math.ceil(min_speech_ms / chunk_ms)
        self._max_chunks = math.ceil(max_utterance_ms / chunk_ms)

        # ONNX 런타임이 PyTorch보다 CPU에서 가볍다. 서버에서 여러 스트림을 받으려면 중요하다.
        self._vad = load_silero_vad(onnx=True)

        # 발화가 시작되기 '전' 구간을 계속 들고 있다가, 발화가 시작되면 앞에 붙인다.
        # 이게 없으면 VAD가 반응하는 사이에 첫 음절이 잘린다. 비속어는 짧아서 치명적이다.
        self._pre_roll = collections.deque(maxlen=math.ceil(pre_roll_ms / chunk_ms))

        self._buffer: list[np.ndarray] = []
        self._speech_chunks = 0
        self._silence_chunks = 0
        self._speaking = False

        self._seq = 0
        self._samples_seen = 0
        self._stream_start_ts: Optional[float] = None
        self._utt_start_sample = 0

    def feed(self, samples: np.ndarray) -> Optional[Utterance]:
        """청크 하나를 넣는다. 발화가 완성됐으면 Utterance, 아니면 None."""
        if self._stream_start_ts is None:
            self._stream_start_ts = time.time()

        prob = self._vad(torch.from_numpy(samples), self.sample_rate).item()
        self._samples_seen += len(samples)

        if prob >= self.threshold:
            if not self._speaking:
                self._start_utterance(len(samples))
            self._buffer.append(samples)
            self._speech_chunks += 1  # 버퍼엔 무음도 섞이므로 음성 청크만 따로 센다
            self._silence_chunks = 0
        elif self._speaking:
            self._buffer.append(samples)  # 어미가 잘리지 않게 무음도 잠시 포함한다
            self._silence_chunks += 1
        else:
            self._pre_roll.append(samples)
            return None

        if self._silence_chunks >= self._silence_limit:
            return self._flush(final=True)
        if len(self._buffer) >= self._max_chunks:
            # 아직 말하는 중이지만 일단 끊어 보낸다. 안 그러면 말이 끝날 때까지
            # STT가 시작조차 못 해서 "1초 안에 경고"가 불가능해진다.
            return self._flush(final=False)
        return None

    def close(self) -> Optional[Utterance]:
        """스트림이 끝났을 때 남은 발화를 마저 꺼낸다."""
        if not self._speaking:
            return None
        return self._flush(final=True)

    def _start_utterance(self, chunk_len: int) -> None:
        self._speaking = True
        self._buffer = list(self._pre_roll)
        pre_roll_len = sum(len(c) for c in self._buffer)
        # 이 청크의 시작 위치에서 pre-roll 길이만큼 앞으로
        self._utt_start_sample = self._samples_seen - chunk_len - pre_roll_len

    def _flush(self, final: bool) -> Optional[Utterance]:
        utt = None
        if self._speech_chunks >= self._min_speech:
            pcm = np.concatenate(self._buffer)
            start_ts = self._stream_start_ts + self._utt_start_sample / self.sample_rate
            self._seq += 1
            utt = Utterance(
                pcm=pcm,
                start_ts=start_ts,
                end_ts=start_ts + len(pcm) / self.sample_rate,
                seq=self._seq,
                forced=not final,
                session_id=self.session_id,
            )

        self._buffer = []
        self._speech_chunks = 0
        self._silence_chunks = 0

        if final:
            self._speaking = False
            self._pre_roll.clear()
            # Silero는 내부 상태를 유지하므로 발화가 끝날 때마다 초기화한다.
            self._vad.reset_states()
        else:
            # 강제로 끊은 것뿐 발화는 이어지고 있다. VAD 상태도 유지해야 한다.
            self._utt_start_sample = self._samples_seen

        return utt
