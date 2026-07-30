"""
반복 질문 Top N (/questions) — 계약: ../CHAT_AI_API.md 4장

"같은 걸 묻는 사람이 몇 명인가"를 세어 스트리머에게 답할 거리를 알려준다.

## 왜 임베딩이 필요한가 (실측)

일정 관련 질문을 VOD에서 세어봤다:

    콘서트 같이보기  444건 → 서로 다른 표현 426개
    팰월드 게임       89건 →              89개 (전부 다름)
    데바데 내전      179건 →             178개

"언제 하냐 / 언제함? / 언제 나오나요? / 언제가나요"가 전부 1회씩 흩어진다.
완전일치로 세면 최대 5회라 "42명이 물었다"가 나올 수 없다. **의미 클러스터링이
이 기능 자체이지 부가 기능이 아니다.**

## 왜 3단 필터인가 (실측)

물음표 채팅 10,814건에서:

    필터 없음        상위가 `???`(318) `ㅇㄴ?`(191) — 전부 리액션
    is_reaction만    `난가?`(69) `어라?`(68) `실수?`(58) — 완성형 한글이라 안 걸림
    + 내용 4자 이상  쓰레기 사라짐

## 왜 Redis인가 (인메모리 아님)

`window.py`와 같은 이유다 — uvicorn 워커가 여러 개여도 목록이 쪼개지면 안 되고,
재배포해도 방송 중 집계가 끊기면 안 된다. 다만 **중심점 벡터는 Redis에 넣지
않는다**(2,000개 × 12KB = 24MB). 락을 쥔 워커의 메모리에만 두고, 워커가 죽거나
바뀌면 `q:text`의 대표 문장을 다시 임베딩해 복원한다(1회 배치, ~2초).

키 스키마 (전부 DB 1, TTL 12시간):

    chat:{bid}:q:seq        STRING  questionId 발급 카운터 (INCR, 재사용 안 함)
    chat:{bid}:q:text       HASH    qid -> 대표 문장
    chat:{bid}:q:tc:{qid}   HASH    표현 -> 횟수 (대표 문장 산출용)
    chat:{bid}:q:msgs       HASH    qid -> 총 메시지 수
    chat:{bid}:q:first/last HASH    qid -> 최초/마지막 시각(ms)
    chat:{bid}:q:rank       ZSET    qid -> askers 수 (Top N 조회 = ZREVRANGE 한 번)
    chat:{bid}:q:a:{qid}    SET     그 질문을 한 uid들 (SADD 반환값으로 신규 판정)
    chat:{bid}:q:exact      HASH    정규화 텍스트 -> qid (임베딩 30% 절감)
    chat:{bid}:q:faq        HASH    qid -> 'FAQ' | 'CONTEXT' (LLM 판정 캐시)
    chat:{bid}:q:pending    LIST    임베딩 대기열 (워커가 소비)
    chat:{bid}:q:lock       STRING  워커 싱글톤 락

`questionId`는 절대 재사용하지 않는다. Spring이 "7번 지웠음"을 DB에 저장하므로
ID가 흔들리면 엉뚱한 질문이 숨겨진다.
"""
import json
import logging
import threading
import time

import numpy as np

from .config import (EMBED_MODEL, Q_BATCH_SEC, Q_BATCH_SIZE, Q_FAQ_JUDGE_MAX,
                     Q_LOCK_SEC, Q_MAX_CLUSTERS, Q_MIN_ASKERS,
                     Q_MIN_CONTENT_LEN, Q_PENDING_MAX, Q_SIM_THRESHOLD, Q_TTL)
from .reaction import _REACT_CHARS, is_reaction
from .text_norm import normalize

log = logging.getLogger('chatai')

QUESTION_LABEL = '혼란/질문'

# 판정 기준을 "답변 가능성" 하나로 좁혔다. 첫 버전은 FAQ 예시에 "게임 선택 이유"를
# 넣었더니 게임 얘기면 전부 FAQ로 기울어, 롤·데바데 방송에서 '저게 안맞아?'
# '왼쪽 3개?'가 FAQ로 나왔다. 실제 오답을 반례로 넣고 게임 편향을 명시적으로 눌렀다.
FAQ_PROMPT = """인터넷 방송 채팅의 질문들이다. 각각을 FAQ / CONTEXT로 분류하라.

판정 기준은 하나다: **방송 화면을 보지 않고 질문만 들어도 스트리머가 답할 수 있는가?**

FAQ — 답할 수 있다.
  방송 일정   "몇 시까지 하세요?" "내일도 방송해요?" "다음 방송 언제예요?"
  장비·설정   "마우스 뭐 쓰세요?" "사양이 어떻게 되나요?"
  개인 근황   "여행 가나요?" "그 영화 봤어요?"
  외부 정보   "롤체 유출된 거 보셨나요?" "합방 확정인가요?"

CONTEXT — 답할 수 없다. 지금 화면이나 직전 대화를 알아야 한다.
  지시어      "저게 안 맞아?" "이거 뭐야" "그거 하면 살아?"
  진행 중인 판 "핵파티야?" "전판 제리니?" "노궁인 걸 몰랐나"
  숫자·위치만 "왼쪽 3개?" "9시 3개가 좋지 않나"
  대화 반응   "안 고마워?" "진짜 뭐함?" "내가 뭘 본 거야"

주의할 두 가지:
- 게임 얘기라고 무조건 CONTEXT는 아니다. 지금 판을 봐야 하면 CONTEXT("저게 안맞아?"),
  게임 자체에 대한 물음이면 FAQ("데바데도 브레이킹 있음?" "클래식 나오면 레이스 돌아옴?").
- 앞으로의 계획을 묻는 것은 화면과 무관하므로 FAQ다("오늘은 뭐함" "2부는 뭐해요?").

출력은 각 줄에 번호와 판정만. 설명 금지.
예) 1 FAQ

질문:
{items}"""


def content_len(text):
    """리액션 문자를 뺀 실질 길이. '난가?' → 2, '오늘 몇 시까지 하세요?' → 9"""
    return sum(1 for ch in text if ch not in _REACT_CHARS)


def is_askable(text):
    """답할 거리가 되는 질문인가. 3단 필터 중 라벨을 뺀 나머지 둘."""
    return not is_reaction(text) and content_len(text) >= Q_MIN_CONTENT_LEN


class QuestionTracker:
    """방송별 반복 질문 집계.

    embed_fn: (list[str]) -> np.ndarray (n, dim)  — 테스트에선 가짜를 주입한다.
              None이면 기능이 꺼진다(GMS_KEY 없음). ingest는 정상 동작한다.
    llm_fn  : (prompt: str) -> str  — FAQ 판정용. None이면 type이 전부 None이 되고
              목록은 그대로 나온다(판정만 빠짐).
    """

    def __init__(self, r, embed_fn, llm_fn=None):
        self.r = r
        self.embed_fn = embed_fn
        self.llm_fn = llm_fn
        self._centroids = {}          # bid -> (qids: list[int], mat: np.ndarray)
        self._stop = threading.Event()
        self._thread = None

    # ---------------------------------------------------------------- 키

    def _k(self, bid, suffix):
        return f'chat:{bid}:q:{suffix}'

    # ---------------------------------------------------------------- 적재

    def offer(self, bid, uid, text, ts_ms=None):
        """/ingest에서 호출. 대기열에 넣기만 하고 즉시 반환한다.

        임베딩을 여기서 하면 안 된다 — GMS가 500건에 1.9초인데 /ingest의
        계약 타임아웃은 2초다. 큐에 넣는 비용은 마이크로초.
        """
        if self.embed_fn is None or not is_askable(text):
            return False
        payload = json.dumps({'u': uid, 't': text,
                              'ms': int(ts_ms if ts_ms is not None else time.time() * 1000)},
                             ensure_ascii=False)
        key = self._k(bid, 'pending')
        pipe = self.r.pipeline(transaction=False)
        pipe.lpush(key, payload)
        pipe.ltrim(key, 0, Q_PENDING_MAX - 1)   # GMS 장애 시 무한 증가 방지
        pipe.expire(key, Q_TTL)
        pipe.execute()
        return True

    # ---------------------------------------------------------------- 조회

    def top(self, bid, limit=5, exclude=()):
        """askers 상위 N. exclude된 qid는 제외하고 나서 상위를 뽑으므로
        지운 자리를 다음 순위가 채운다 (계약 4장)."""
        exclude = {int(x) for x in exclude}
        # 여유분을 더 읽어야 exclude를 빼고도 limit개가 남는다
        raw = self.r.zrevrange(self._k(bid, 'rank'), 0, limit + len(exclude) + 20,
                               withscores=True)
        picked = []
        for qid_raw, score in raw:
            qid = int(qid_raw)
            if qid in exclude or int(score) < Q_MIN_ASKERS:
                continue
            picked.append((qid, int(score)))
            if len(picked) >= limit:
                break
        if not picked:
            return {'questions': [], **self._meta(bid)}

        pipe = self.r.pipeline(transaction=False)
        for qid, _ in picked:
            pipe.hget(self._k(bid, 'text'), qid)
            pipe.hget(self._k(bid, 'msgs'), qid)
            pipe.hget(self._k(bid, 'first'), qid)
            pipe.hget(self._k(bid, 'last'), qid)
            pipe.hget(self._k(bid, 'faq'), qid)
        out = pipe.execute()

        questions = []
        for i, (qid, askers) in enumerate(picked):
            text, msgs, first, last, faq = out[i * 5:i * 5 + 5]
            questions.append({
                'questionId': qid,
                'text': _s(text),
                'type': _s(faq),          # 'FAQ' | 'CONTEXT' | None(아직 판정 전)
                'askers': askers,
                'messages': int(msgs or 0),
                'firstAskedAt': int(first or 0),
                'lastAskedAt': int(last or 0),
            })
        return {'questions': questions, **self._meta(bid)}

    def _meta(self, bid):
        pipe = self.r.pipeline(transaction=False)
        pipe.zcard(self._k(bid, 'rank'))
        pipe.llen(self._k(bid, 'pending'))
        total, pending = pipe.execute()
        return {'totalQuestions': int(total),
                'pendingEmbedding': int(pending),
                'updatedAt': int(time.time() * 1000)}

    def exists(self, bid):
        return self.r.exists(self._k(bid, 'rank'), self._k(bid, 'pending')) > 0

    def delete(self, bid):
        """DELETE /broadcasts/{bid}에서 window.delete()가 chat:{bid}:* 를
        통째로 지우므로 별도 호출은 불필요하다. 프로세스 캐시만 비운다."""
        self._centroids.pop(str(bid), None)

    # ---------------------------------------------------------------- 워커

    def start(self):
        if self.embed_fn is None:
            log.warning('임베딩 함수 없음 — /questions 비활성 (ingest는 정상)')
            return self
        self._thread = threading.Thread(target=self._loop, daemon=True,
                                        name='question-worker')
        self._thread.start()
        return self

    def stop(self, timeout=3):
        self._stop.set()
        if self._thread:
            self._thread.join(timeout=timeout)

    def _loop(self):
        """대기열이 찼거나 주기가 지나면 배치 처리. 예외가 나도 죽지 않는다."""
        last = time.monotonic()
        while not self._stop.wait(1.0):
            try:
                due = time.monotonic() - last >= Q_BATCH_SEC
                for bid in self._active_broadcasts():
                    n = self.r.llen(self._k(bid, 'pending'))
                    if n and (due or n >= Q_BATCH_SIZE):
                        self.flush(bid)
                if due:
                    last = time.monotonic()
            except Exception:
                log.exception('question-worker 배치 실패 — 계속 진행')

    def _active_broadcasts(self):
        """대기열이 있는 방송들. SCAN이라 키가 많아도 블로킹되지 않는다."""
        out = []
        for key in self.r.scan_iter(match='chat:*:q:pending', count=200):
            out.append(_s(key).split(':')[1])
        return out

    # ---------------------------------------------------------------- 처리

    def flush(self, bid):
        """대기열을 소비해 클러스터에 반영. 워커 싱글톤 락으로 보호한다."""
        bid = str(bid)
        lock = self._k(bid, 'lock')
        if not self.r.set(lock, '1', nx=True, ex=Q_LOCK_SEC):
            return 0                       # 다른 워커가 처리 중
        try:
            key = self._k(bid, 'pending')
            raws = self.r.lrange(key, 0, Q_BATCH_SIZE - 1)
            if not raws:
                return 0
            # 소비한 만큼만 제거 — 그 사이 들어온 것은 다음 배치로
            self.r.ltrim(key, len(raws), -1)
            items = [json.loads(_s(x)) for x in raws]
            items.reverse()                # LPUSH라 최신이 앞 → 시간순
            self._absorb(bid, items)
            self._judge_new(bid)           # 노출 후보만 FAQ 판정 (캐시됨)
            return len(items)
        finally:
            self.r.delete(lock)

    def _judge_new(self, bid):
        """노출 대상(askers >= 최소 인원) 중 아직 판정 안 된 것만 LLM에 묻는다.

        GET에서 부르지 않는 이유: LLM 호출 1~2초가 폴링 응답에 그대로 얹힌다.
        판정 대상이 개별 채팅이 아니라 클러스터 대표 문장이라 방송당 수백 건이고,
        한 번 판정하면 캐시되므로 같은 질문을 다시 묻지 않는다.
        """
        if self.llm_fn is None:
            return
        ranked = self.r.zrevrangebyscore(self._k(bid, 'rank'), '+inf', Q_MIN_ASKERS)
        if not ranked:
            return
        qids = [int(q) for q in ranked][:Q_FAQ_JUDGE_MAX * 2]
        cached = self.r.hmget(self._k(bid, 'faq'), qids)
        todo = [q for q, c in zip(qids, cached) if c is None][:Q_FAQ_JUDGE_MAX]
        if not todo:
            return

        texts = self.r.hmget(self._k(bid, 'text'), todo)
        pairs = [(q, _s(t)) for q, t in zip(todo, texts) if t]
        if not pairs:
            return

        listing = '\n'.join(f'{i + 1}. {t}' for i, (_, t) in enumerate(pairs))
        try:
            reply = self.llm_fn(FAQ_PROMPT.format(items=listing))
        except Exception:
            log.exception('FAQ 판정 실패 — 다음 배치에서 재시도')
            return

        verdicts = {}
        for line in reply.splitlines():
            parts = line.replace('.', ' ').split()
            if len(parts) >= 2 and parts[0].isdigit():
                idx = int(parts[0]) - 1
                tag = 'FAQ' if 'FAQ' in parts[1].upper() else 'CONTEXT'
                if 0 <= idx < len(pairs):
                    verdicts[pairs[idx][0]] = tag
        if verdicts:
            self.r.hset(self._k(bid, 'faq'), mapping=verdicts)
            self.r.expire(self._k(bid, 'faq'), Q_TTL)
            log.info('FAQ 판정 %d건 (broadcast=%s)', len(verdicts), bid)

    def _absorb(self, bid, items):
        exact_key = self._k(bid, 'exact')

        # ① 완전일치 캐시 — 이미 본 문장은 임베딩 없이 카운트만 (실측 30% 절감)
        norms = [normalize(it['t']) for it in items]
        hits = self.r.hmget(exact_key, norms) if norms else []
        need = []
        for it, norm, hit in zip(items, norms, hits):
            if not norm:
                continue
            if hit is not None:
                self._bump(bid, int(hit), it['u'], norm, it['ms'])
            else:
                need.append((it, norm))

        if not need:
            return

        # ② 남은 것만 임베딩 (배치 1회 호출)
        vecs = self.embed_fn([n for _, n in need])
        vecs = np.asarray(vecs, dtype=np.float32)
        vecs /= np.linalg.norm(vecs, axis=1, keepdims=True) + 1e-9

        qids, mat = self._load_centroids(bid, vecs.shape[1])

        # ③ 온라인 클러스터링 — 기존 중심점과 코사인, 임계 이상이면 편입
        for (it, norm), v in zip(need, vecs):
            qid = None
            if len(qids):
                sims = mat @ v
                best = int(sims.argmax())
                if float(sims[best]) >= Q_SIM_THRESHOLD:
                    qid = qids[best]
                    # 중심점 갱신: 누적 평균 후 재정규화
                    mat[best] = mat[best] + (v - mat[best]) / max(2, self._msgs(bid, qid) + 1)
                    mat[best] /= np.linalg.norm(mat[best]) + 1e-9
            if qid is None:
                qid = int(self.r.incr(self._k(bid, 'seq')))
                self.r.expire(self._k(bid, 'seq'), Q_TTL)
                qids.append(qid)
                mat = v[None, :] if not len(mat) else np.vstack([mat, v])
            self.r.hset(exact_key, norm, qid)
            self.r.expire(exact_key, Q_TTL)
            self._bump(bid, qid, it['u'], norm, it['ms'])

        self._centroids[bid] = (qids, mat)
        self._evict(bid)

    def _bump(self, bid, qid, uid, norm, ms):
        """한 건 반영. askers는 SADD 반환값으로 '신규 인원'일 때만 증가시킨다
        — 한 사람이 10번 물어도 1명으로 세야 '다들 궁금해한다'가 된다."""
        pipe = self.r.pipeline(transaction=False)
        pipe.hincrby(self._k(bid, 'msgs'), qid, 1)
        pipe.hincrby(self._k(bid, f'tc:{qid}'), norm, 1)
        pipe.hsetnx(self._k(bid, 'first'), qid, ms)
        pipe.hset(self._k(bid, 'last'), qid, ms)
        for suffix in ('msgs', f'tc:{qid}', 'first', 'last'):
            pipe.expire(self._k(bid, suffix), Q_TTL)
        pipe.execute()

        if uid:
            added = self.r.sadd(self._k(bid, f'a:{qid}'), uid)
            self.r.expire(self._k(bid, f'a:{qid}'), Q_TTL)
            if added:
                self.r.zincrby(self._k(bid, 'rank'), 1, qid)
        else:
            # uid 없는 건도 클러스터는 존재해야 하므로 0점으로 등록
            self.r.zincrby(self._k(bid, 'rank'), 0, qid)
        self.r.expire(self._k(bid, 'rank'), Q_TTL)

        self._refresh_text(bid, qid)

    def _refresh_text(self, bid, qid):
        """대표 문장 = 그 클러스터에서 가장 많이 쓰인 표현."""
        tc = self.r.hgetall(self._k(bid, f'tc:{qid}'))
        if not tc:
            return
        best = max(tc.items(), key=lambda kv: int(kv[1]))[0]
        self.r.hset(self._k(bid, 'text'), qid, _s(best))
        self.r.expire(self._k(bid, 'text'), Q_TTL)

    def _msgs(self, bid, qid):
        v = self.r.hget(self._k(bid, 'msgs'), qid)
        return int(v or 0)

    def _load_centroids(self, bid, dim):
        """중심점은 Redis에 두지 않는다(2,000개 × 12KB = 24MB).
        캐시에 없으면 대표 문장을 다시 임베딩해 복원한다 — 재시작·워커 교체 대응."""
        cached = self._centroids.get(bid)
        if cached is not None:
            return cached

        texts = self.r.hgetall(self._k(bid, 'text'))
        if not texts:
            return [], np.zeros((0, dim), dtype=np.float32)

        qids = [int(k) for k in texts]
        log.info('중심점 복원: broadcast=%s 클러스터 %d개 재임베딩', bid, len(qids))
        vecs = np.asarray(self.embed_fn([_s(texts[k]) for k in texts]), dtype=np.float32)
        vecs /= np.linalg.norm(vecs, axis=1, keepdims=True) + 1e-9
        self._centroids[bid] = (qids, vecs)
        return qids, vecs

    def _evict(self, bid):
        """상한 초과 시 askers가 가장 적은 것부터 제거.
        제거된 qid는 재사용하지 않는다 (Spring의 dismissed 기록 보호)."""
        qids, mat = self._centroids.get(bid, ([], None))
        if len(qids) <= Q_MAX_CLUSTERS:
            return
        rank = dict(self.r.zrange(self._k(bid, 'rank'), 0, -1, withscores=True))
        scored = sorted(qids, key=lambda q: rank.get(str(q).encode(), rank.get(str(q), 0)) or 0)
        drop = set(scored[:len(qids) - Q_MAX_CLUSTERS])
        keep_idx = [i for i, q in enumerate(qids) if q not in drop]

        pipe = self.r.pipeline(transaction=False)
        for q in drop:
            pipe.hdel(self._k(bid, 'text'), q)
            pipe.hdel(self._k(bid, 'msgs'), q)
            pipe.hdel(self._k(bid, 'first'), q)
            pipe.hdel(self._k(bid, 'last'), q)
            pipe.hdel(self._k(bid, 'faq'), q)
            pipe.delete(self._k(bid, f'tc:{q}'))
            pipe.delete(self._k(bid, f'a:{q}'))
            pipe.zrem(self._k(bid, 'rank'), q)
        pipe.execute()

        self._centroids[bid] = ([qids[i] for i in keep_idx], mat[keep_idx])
        log.info('클러스터 상한 초과 — %d개 제거 (broadcast=%s)', len(drop), bid)


def _s(v):
    """redis-py는 bytes, fakeredis 설정에 따라 str이 온다 (window.py와 동일)."""
    return v.decode() if isinstance(v, bytes) else v
