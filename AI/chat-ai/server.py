"""
감정 분류 추론 서버 (FastAPI)

Spring 백엔드가 HTTP로 호출한다. 전처리·모델이 이 프로세스 안에 함께 있으므로
학습 때와 서빙 때의 전처리가 어긋날 여지가 없다 (train-serve skew 차단).

실행:
    python fetch_model.py          # 최초 1회, 모델 내려받기
    uvicorn server:app --host 0.0.0.0 --port 8001

문서:
    http://localhost:8001/docs     # Swagger UI에서 바로 시험 가능
"""
import os

from contextlib import asynccontextmanager

from fastapi import FastAPI
from pydantic import BaseModel, Field

from chatai.classifier import LABELS, ChatClassifier

MODEL_DIR = os.getenv('CHATAI_MODEL_DIR', 'models/kcelectra-chat-8emo-onnx')
# int8은 약 2배 빠르지만 macro-F1이 1.9점 낮다. 기본은 정확도 우선(fp32).
USE_INT8 = os.getenv('CHATAI_INT8', '0') == '1'

clf = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global clf
    clf = ChatClassifier(MODEL_DIR, quantized=USE_INT8)
    yield


app = FastAPI(title='채팅 감정 분류기', version='1.0',
              description='치지직 채팅 8감정 분류 (KcELECTRA 증류 · CPU ONNX)',
              lifespan=lifespan)


class ClassifyRequest(BaseModel):
    texts: list[str] = Field(..., description='채팅 원문 리스트. 전처리는 서버가 한다.')
    include_probs: bool = Field(True, description='False면 label/score만 반환(응답 크기 절감)')


class AggregateRequest(BaseModel):
    texts: list[str] = Field(..., description='집계할 채팅 원문 리스트 (예: 3분 윈도우)')


@app.get('/health')
def health():
    return {'status': 'ok', 'model': MODEL_DIR,
            'quantized': USE_INT8, 'labels': LABELS}


@app.post('/classify')
def classify(req: ClassifyRequest):
    """
    개별 분류. 입력 순서를 그대로 유지해 반환한다.

    응답의 probs를 버리지 말 것 — 여론 분포를 낼 때는 argmax 카운트보다
    확률 합산이 정확하다 (/aggregate 참고).
    """
    res = clf.predict(req.texts)
    if not req.include_probs:
        res = [{'label': r['label'], 'score': r['score']} for r in res]
    return {'count': len(res), 'results': res}


@app.post('/aggregate')
def aggregate(req: AggregateRequest):
    """
    여론 분포 집계 (FR-F-01 실시간 채팅 여론 요약용).

    확률을 합산해 라벨별 비율을 낸다. 합은 1.0이다.
    애매한 채팅이 한쪽으로 몰리지 않고 애매한 만큼만 반영되므로
    개별 분류를 세어 만든 분포보다 실제에 가깝다.
    """
    dist = clf.aggregate(req.texts)
    top = max(dist, key=dist.get) if req.texts else None
    return {'count': len(req.texts), 'distribution': dist, 'dominant': top}
