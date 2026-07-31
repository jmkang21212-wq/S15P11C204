"""
감정 분류 추론 서버 (FastAPI)

Spring 백엔드가 HTTP로 호출한다. 전처리·모델이 이 프로세스 안에 함께 있으므로
학습 때와 서빙 때의 전처리가 어긋날 여지가 없다 (train-serve skew 차단).

실행:
    python fetch_model.py          # 최초 1회, 모델 내려받기
    uvicorn server:app --host 0.0.0.0 --port 8001

문서:
    http://localhost:8001/docs     # Swagger UI에서 바로 시험 가능
    계약: ../CHAT_AI_API.md
"""
import logging
import os

from contextlib import asynccontextmanager

import redis as redis_lib
from fastapi import Depends, FastAPI, Header, HTTPException, Response
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from chatai.classifier import LABELS, ChatClassifier
from chatai.config import (CHATAI_TOKEN, EMBED_MODEL, GMS_BASE_URL, GMS_KEY,
                           REDIS_URL, SUMMARY_MODEL, SUMMARY_REASONING)
from chatai.questions import QUESTION_LABEL, QuestionTracker
from chatai.summary import SummaryRejected, SummaryService
from chatai.window import BroadcastWindow

log = logging.getLogger('chatai')

MODEL_DIR = os.getenv('CHATAI_MODEL_DIR', 'models/kcelectra-chat-8emo-onnx')
# int8은 약 2배 빠르지만 macro-F1이 1.9점 낮다. 기본은 정확도 우선(fp32).
USE_INT8 = os.getenv('CHATAI_INT8', '0') == '1'

clf = None
rdb = None
summarizer = None
questions = None


def _make_llm_fn():
    """GMS(OpenAI 호환 프록시) 호출 함수. 키가 없으면 None — /summary만 꺼진다."""
    if not GMS_KEY:
        return None
    from openai import OpenAI
    client = OpenAI(api_key=GMS_KEY, base_url=GMS_BASE_URL, timeout=20)

    def call(prompt):
        r = client.chat.completions.create(
            model=SUMMARY_MODEL,
            messages=[{'role': 'user', 'content': prompt}],
            reasoning_effort=SUMMARY_REASONING,
        )
        return r.choices[0].message.content.strip()

    return call


def _make_embed_fn():
    """GMS 임베딩 호출 함수. 키가 없으면 None — /questions만 꺼진다.

    3-small을 쓰면 안 된다: 실측에서 같은 질문('몇시까지 하세요'~'언제 끝나요')이
    0.225로 다른 질문(0.330)보다 낮게 나와 임계값으로 가를 수 없었다.
    """
    if not GMS_KEY:
        return None
    from openai import OpenAI
    client = OpenAI(api_key=GMS_KEY, base_url=GMS_BASE_URL, timeout=60)

    def call(texts):
        r = client.embeddings.create(model=EMBED_MODEL, input=list(texts))
        return [d.embedding for d in r.data]

    return call


@asynccontextmanager
async def lifespan(app: FastAPI):
    global clf, rdb, summarizer, questions
    clf = ChatClassifier(MODEL_DIR, quantized=USE_INT8)
    # decode_responses=False 통일 — window.py가 bytes/str 둘 다 처리한다
    rdb = redis_lib.Redis.from_url(REDIS_URL, socket_timeout=2, socket_connect_timeout=2)
    try:
        rdb.ping()
        log.info('Redis 연결: %s', REDIS_URL)
    except redis_lib.RedisError:
        # 기동은 계속한다 — /classify·/aggregate는 Redis 없이도 동작해야 하고,
        # Redis가 늦게 떠도 ingest가 자동으로 회복된다.
        log.warning('Redis 접속 실패(%s) — /ingest는 503, 분류 API는 정상', REDIS_URL)
    llm_fn = _make_llm_fn()
    if llm_fn is None:
        log.warning('GMS_KEY 없음 — /summary는 503, 나머지는 정상')
    summarizer = SummaryService(rdb, llm_fn)
    # llm_fn 공유: 질문 목록은 클러스터 대표 문장만 FAQ/CONTEXT로 판정한다
    questions = QuestionTracker(rdb, _make_embed_fn(), llm_fn).start()
    yield
    questions.stop()
    rdb.close()


app = FastAPI(title='채팅 감정 분류기', version='1.1',
              description='치지직 채팅 8감정 분류 + 실시간 호응도 (KcELECTRA 증류 · CPU ONNX)',
              lifespan=lifespan)


def require_token(authorization: str | None = Header(default=None)):
    """CHATAI_TOKEN이 설정된 경우에만 Bearer 검사. 비어 있으면 통과(로컬 개발)."""
    if not CHATAI_TOKEN:
        return
    if authorization != f'Bearer {CHATAI_TOKEN}':
        raise HTTPException(status_code=401, detail='invalid token')


class ClassifyRequest(BaseModel):
    texts: list[str] = Field(..., description='채팅 원문 리스트. 전처리는 서버가 한다.')
    include_probs: bool = Field(True, description='False면 label/score만 반환(응답 크기 절감)')


class AggregateRequest(BaseModel):
    texts: list[str] = Field(..., description='집계할 채팅 원문 리스트 (예: 3분 윈도우)')


class IngestChat(BaseModel):
    uid: str = Field('', description='치지직 userIdHash. 호응도 계산에 필수 (없으면 유니크 제외)')
    msg: str = Field(..., description='채팅 원문. 전처리하지 말 것')
    ts: int = Field(0, description='epoch ms (참고용 — 윈도우는 서버 도착 시각 기준)')


class IngestRequest(BaseModel):
    viewers: int | None = Field(None, description='현재 시청자 수. 폴링 실패 시 null')
    chats: list[IngestChat] = Field(default_factory=list,
                                    description='최근 1초 수신 채팅. 빈 배열이어도 매초 호출할 것')


@app.get('/health')
def health():
    redis_ok = False
    try:
        redis_ok = bool(rdb) and rdb.ping()
    except redis_lib.RedisError:
        pass
    return {'status': 'ok', 'model': MODEL_DIR,
            'quantized': USE_INT8, 'labels': LABELS, 'redis': redis_ok}


@app.post('/classify', dependencies=[Depends(require_token)])
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


@app.post('/aggregate', dependencies=[Depends(require_token)])
def aggregate(req: AggregateRequest):
    """
    여론 분포 집계 (단발성 — 임의 텍스트 묶음의 분포가 필요할 때).

    실시간 방송 지표는 /broadcasts/{id}/ingest 응답의 window를 쓸 것.
    """
    dist = clf.aggregate(req.texts)
    top = max(dist, key=dist.get) if req.texts else None
    return {'count': len(req.texts), 'distribution': dist, 'dominant': top}


@app.post('/broadcasts/{broadcast_id}/ingest', dependencies=[Depends(require_token)])
def ingest(broadcast_id: str, req: IngestRequest):
    """
    1초 배치 분류 + 슬라이딩 윈도우 지표 (계약: ../CHAT_AI_API.md 1장).

    results는 요청 chats와 같은 순서. window는 이번 배치까지 반영된
    최근 180초 지표 — 그대로 프론트에 중계하면 실시간 호응도 화면이 된다.
    """
    if req.chats:
        preds = clf.predict([c.msg for c in req.chats])
        results = [{'label': p['label'], 'score': round(p['score'], 4)} for p in preds]
        # (uid, 원문, top-1 라벨, 확률8) — 원문·라벨은 /summary가 쓴다
        items = [(c.uid, c.msg, p['label'], [p['probs'][lab] for lab in LABELS])
                 for c, p in zip(req.chats, preds)]
        # 질문은 반복 질문 집계 대기열로 (임베딩은 백그라운드 — 여기서 하면
        # GMS 호출 1.9초가 계약 타임아웃 2초를 먹는다)
        for c, p in zip(req.chats, preds):
            if p['label'] == QUESTION_LABEL:
                questions.offer(broadcast_id, c.uid, c.msg, c.ts or None)
    else:
        results, items = [], []

    try:
        window = BroadcastWindow(rdb, broadcast_id).add_batch(req.viewers, items)
    except redis_lib.RedisError as e:
        # 계약 '반드시 볼 것 1': Spring은 503을 받으면 이 배치를 건너뛴다.
        # 지표 없는 반쪽 응답보다 명확한 실패가 낫다 (원본은 Spring에 있어 유실 없음).
        raise HTTPException(status_code=503, detail=f'redis unavailable: {type(e).__name__}')

    return {'results': results, 'window': window}


class SummaryRequest(BaseModel):
    mode: str = Field('AUTO', description='AUTO(3분 주기 스케줄러) | MANUAL(버튼). 규칙은 동일, 로그 구분용')


@app.post('/broadcasts/{broadcast_id}/summary', dependencies=[Depends(require_token)])
def summary_post(broadcast_id: str, req: SummaryRequest):
    """
    LLM 여론 요약 생성 (계약: ../CHAT_AI_API.md 2장).

    가드: 채팅 30건 미만 422 · 쿨다운 60초 409 · 진행 중 409.
    409 IN_FLIGHT를 받으면 잠시 후 GET으로 방금 생성된 요약을 받으면 된다.
    """
    log.info('summary 요청: broadcast=%s mode=%s', broadcast_id, req.mode)
    try:
        return summarizer.generate(broadcast_id, req.mode)
    except SummaryRejected as e:
        return JSONResponse(e.payload, status_code=e.status)
    except redis_lib.RedisError as e:
        raise HTTPException(status_code=503, detail=f'redis unavailable: {type(e).__name__}')
    except Exception as e:
        # LLM 호출 실패 (GMS 장애·타임아웃 등) — 락은 finally로 이미 해제됨
        log.error('summary LLM 실패: %s', e)
        raise HTTPException(status_code=502, detail=f'llm failed: {type(e).__name__}')


@app.get('/broadcasts/{broadcast_id}/summary', dependencies=[Depends(require_token)])
def summary_get(broadcast_id: str):
    """마지막 요약 캐시 (LLM 미호출·쿨다운 무관). 새로고침·재입장용."""
    try:
        cached = summarizer.get_cached(broadcast_id)
    except redis_lib.RedisError as e:
        raise HTTPException(status_code=503, detail=f'redis unavailable: {type(e).__name__}')
    if cached is None:
        return JSONResponse({'reason': 'NO_SUMMARY'}, status_code=404)
    return {k: v for k, v in cached.items() if not k.startswith('_')}


@app.get('/broadcasts/{broadcast_id}/questions', dependencies=[Depends(require_token)])
def questions_get(broadcast_id: str, limit: int = 5, exclude: str = ''):
    """
    반복 질문 Top N (계약: ../CHAT_AI_API.md 3장).

    exclude: 스트리머가 지운 questionId 목록(쉼표 구분). AI는 무엇이 지워졌는지
    기억하지 않는다 — Spring이 매 요청에 실어 보내고, AI는 제외한 뒤 상위를
    뽑으므로 지운 자리를 다음 순위가 채운다.

    폴링은 10~30초 주기를 권장한다. /ingest(1초)와 달리 목록은 천천히 변한다.
    """
    limit = max(1, min(limit, 50))
    try:
        ids = tuple(int(x) for x in exclude.split(',') if x.strip().isdigit())
    except ValueError:
        ids = ()
    try:
        if not (questions.exists(broadcast_id)
                or BroadcastWindow(rdb, broadcast_id).exists()):
            return JSONResponse({'reason': 'UNKNOWN_BROADCAST'}, status_code=404)
        return questions.top(broadcast_id, limit, ids)
    except redis_lib.RedisError as e:
        raise HTTPException(status_code=503, detail=f'redis unavailable: {type(e).__name__}')


@app.delete('/broadcasts/{broadcast_id}', status_code=204,
            dependencies=[Depends(require_token)])
def delete_broadcast(broadcast_id: str):
    """방송 종료 정리 (멱등). 안 불러도 TTL이 자동 정리한다."""
    try:
        BroadcastWindow(rdb, broadcast_id).delete()   # chat:{bid}:* 전부 (q:도 포함)
        questions.delete(broadcast_id)                # 프로세스 중심점 캐시
    except redis_lib.RedisError:
        pass                    # 어차피 TTL이 지운다 — 종료 처리를 막지 않는다
    return Response(status_code=204)
