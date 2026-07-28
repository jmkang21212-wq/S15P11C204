"""
채팅 감정 분류기 — ONNX 추론 래퍼

학습된 KcELECTRA 모델을 CPU에서 실행한다. GPU 불필요.

⚠️ 전처리는 반드시 text_norm.normalize를 거친다. 이 클래스가 내부에서 처리하므로
   호출하는 쪽은 원문을 그대로 넘기면 된다. 밖에서 따로 전처리하지 말 것
   (이중 적용은 무해하지만, 다른 방식으로 전처리하면 성능이 조용히 떨어진다).

사용법:
    clf = ChatClassifier('models/kcelectra-chat-8emo-onnx')
    clf.predict(['ㅋㅋㅋ 개웃기네', '이거 왜 이래요?'])
    # [{'label': '웃음/재미', 'score': 0.94, 'probs': {...}}, ...]
"""
import os

import numpy as np
import onnxruntime as ort
from transformers import AutoTokenizer

from .labels import LABELS          # 정본은 labels.py — 순서가 모델 출력 인덱스
from .text_norm import normalize

MAX_LEN = 64          # 학습 때와 동일해야 함
DEFAULT_BATCH = 32


class ChatClassifier:
    def __init__(self, model_dir, quantized=True, threads=None):
        """
        model_dir : ONNX 모델 폴더 (fetch_model.py가 받아둔 경로)
        quantized : True면 int8(129MB), False면 fp32(511MB).
                    int8은 2배 빠르지만 macro-F1이 약 1.9점 낮다.
                    현재 배포 기준은 fp32 — 처리량이 부족할 때만 int8로 전환.
        threads   : ONNX Runtime 스레드 수. None이면 코어 수만큼 자동.
        """
        name = 'model.int8.onnx' if quantized else 'model.onnx'
        path = os.path.join(model_dir, name)
        if not os.path.exists(path):
            raise FileNotFoundError(
                f'모델이 없습니다: {path}\n'
                f'먼저 실행하세요:  python fetch_model.py')

        opts = ort.SessionOptions()
        if threads:
            opts.intra_op_num_threads = threads
        self.session = ort.InferenceSession(
            path, opts, providers=['CPUExecutionProvider'])
        self._inputs = {i.name for i in self.session.get_inputs()}
        self.tokenizer = AutoTokenizer.from_pretrained(model_dir)
        self.labels = LABELS

    def predict(self, texts, batch_size=DEFAULT_BATCH):
        """원문 리스트 → 라벨·확률 리스트. 입력 순서를 그대로 유지한다."""
        if not texts:
            return []
        out = []
        for i in range(0, len(texts), batch_size):
            chunk = [normalize(t) for t in texts[i:i + batch_size]]
            # 배치별 패딩 — 전체 일괄 패딩은 최장 문장에 맞춰 부풀어 느려진다
            enc = self.tokenizer(chunk, truncation=True, max_length=MAX_LEN,
                                 padding=True, return_tensors='np')
            feed = {k: v for k, v in enc.items() if k in self._inputs}
            logits = self.session.run(None, feed)[0]
            for row in _softmax(logits):
                idx = int(row.argmax())
                out.append({
                    'label': LABELS[idx],
                    'score': float(row[idx]),
                    'probs': {LABELS[j]: float(p) for j, p in enumerate(row)},
                })
        return out

    def aggregate(self, texts, batch_size=DEFAULT_BATCH):
        """
        여론 분포 집계 (FR-F-01용).

        개별 라벨을 argmax로 세지 않고 **확률을 그대로 합산**한다.
        애매한 채팅(웃음 0.45 / 중립 0.40)은 애매한 만큼만 각 라벨에 기여하므로,
        argmax 카운트보다 실제 분포에 가깝다. 3분 윈도우 요약처럼
        '개별 라벨이 아니라 비율'이 필요한 곳에서는 이쪽을 쓸 것.
        """
        preds = self.predict(texts, batch_size)
        if not preds:
            return {lab: 0.0 for lab in LABELS}
        total = {lab: 0.0 for lab in LABELS}
        for p in preds:
            for lab, v in p['probs'].items():
                total[lab] += v
        n = len(preds)
        return {lab: v / n for lab, v in total.items()}


def _softmax(x):
    x = x - x.max(axis=-1, keepdims=True)
    e = np.exp(x)
    return e / e.sum(axis=-1, keepdims=True)
