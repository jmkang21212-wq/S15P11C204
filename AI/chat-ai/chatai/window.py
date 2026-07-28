"""
방송별 슬라이딩 윈도우 (Redis 기반) — /ingest의 핵심 로직

최근 WINDOW_SEC(기본 180초) 동안의 채팅으로 세 지표를 낸다:
  uniqueChatters  최근 3분 안에 채팅 친 서로 다른 사람 수
  engagement      uniqueChatters ÷ 시청자수 ("시청자의 몇 %가 채팅에 참여 중인가")
  distribution    감정 8라벨 여론 분포 (확률 합산 방식, 합 1.0)

저장을 Redis에 두는 이유 (인메모리 대비):
  - 서버를 재배포/재시작해도 방송 중 지표가 끊기지 않는다
  - uvicorn 워커를 늘려도 지표가 워커마다 쪼개지지 않는다
  - RedisInsight(:5540)로 윈도우 상태를 눈으로 확인할 수 있다
  - ZADD·HINCRBYFLOAT가 원자 연산이라 Lock이 필요 없고,
    TTL이 청소를 대신해 유휴 정리 태스크도 필요 없다

키 스키마 (전부 DB 1 — Spring은 DB 0을 쓴다):
  chat:{bid}:u        ZSET   member=uid, score=마지막 채팅 시각(ms)   TTL 10분
  chat:{bid}:b:{sec}  HASH   n(채팅수), e0~e7(감정확률 합) 초단위 버킷  TTL 5분
  chat:{bid}:v        STRING 마지막 viewers                          TTL 10분

시간 기준은 서버 도착 시각이다. 치지직 msgTime은 클라이언트 시계라 신뢰하지 않는다.
"""
import time

from .config import BUCKET_TTL, UID_TTL, WINDOW_SEC
from .labels import LABELS


class BroadcastWindow:
    """방송 하나의 윈도우. redis 클라이언트를 주입받는다 (테스트: fakeredis)."""

    def __init__(self, r, broadcast_id):
        self.r = r
        self.bid = str(broadcast_id)
        self.key_u = f'chat:{self.bid}:u'
        self.key_v = f'chat:{self.bid}:v'

    def _key_b(self, sec):
        return f'chat:{self.bid}:b:{sec}'

    def add_batch(self, viewers, items, now_ms=None):
        """1초 배치를 반영하고 최신 윈도우 지표를 반환한다.

        items  : [(uid, probs)] — probs는 LABELS 순서의 확률 8개
        viewers: None이면 이번 배치에 시청자값이 없다는 뜻 — 기존 값을 유지한다
        빈 배치여도 호출해야 한다. 윈도우가 시간에 따라 미끄러지고
        (조용해진 사람이 빠지고) viewers가 갱신되는 것이 이 호출에서 일어난다.
        """
        now_ms = int(now_ms if now_ms is not None else time.time() * 1000)
        sec = now_ms // 1000
        cutoff = now_ms - WINDOW_SEC * 1000

        pipe = self.r.pipeline(transaction=False)

        # ① 새 채팅 반영 — uid는 ZSET(마지막 활동 시각), 확률은 현재 초 버킷에 증분
        bucket = self._key_b(sec)
        n_new = 0
        for uid, probs in items:
            if uid:                       # uid 없는 메시지(익명 등)는 유니크에서 제외
                pipe.zadd(self.key_u, {uid: now_ms})
            for i, p in enumerate(probs):
                if p:
                    pipe.hincrbyfloat(bucket, f'e{i}', float(p))
            n_new += 1
        if n_new:
            pipe.hincrby(bucket, 'n', n_new)
            pipe.expire(bucket, BUCKET_TTL)

        # ② 윈도우 이탈 — 마지막 채팅이 3분 넘게 조용한 사람 제거
        pipe.zremrangebyscore(self.key_u, 0, cutoff)
        pipe.expire(self.key_u, UID_TTL)

        # ③ viewers 갱신 (None이면 기존 값 유지 — 폴링 실패 프레임)
        if viewers is not None:
            pipe.set(self.key_v, int(viewers), ex=UID_TTL)
        pipe.execute()

        return self.metrics(now_ms)

    def metrics(self, now_ms=None):
        """현재 윈도우 지표. BACKEND_API.md의 window 블록 그대로."""
        now_ms = int(now_ms if now_ms is not None else time.time() * 1000)
        sec = now_ms // 1000

        pipe = self.r.pipeline(transaction=False)
        pipe.zcard(self.key_u)
        pipe.get(self.key_v)
        for s in range(sec - WINDOW_SEC + 1, sec + 1):
            pipe.hgetall(self._key_b(s))
        out = pipe.execute()

        unique = int(out[0])
        raw_v = out[1]
        viewers = int(raw_v) if raw_v is not None else None

        n_total = 0
        e_sum = [0.0] * len(LABELS)
        for h in out[2:]:
            if not h:
                continue
            # redis-py는 bytes로, fakeredis 설정에 따라 str로 올 수 있다
            for k, v in h.items():
                k = k.decode() if isinstance(k, bytes) else k
                if k == 'n':
                    n_total += int(v)
                else:
                    e_sum[int(k[1])] += float(v)

        if n_total:
            dist = {lab: round(e_sum[i] / n_total, 4) for i, lab in enumerate(LABELS)}
            dominant = max(dist, key=dist.get)
        else:
            dist = {lab: 0.0 for lab in LABELS}
            dominant = None

        engagement = round(unique / viewers, 4) if viewers else None

        return {
            'windowSec': WINDOW_SEC,
            'chatCount': n_total,
            'uniqueChatters': unique,
            'viewers': viewers,
            'engagement': engagement,
            'distribution': dist,
            'dominant': dominant,
        }

    def delete(self):
        """이 방송의 키 전부 제거 (멱등). DELETE /broadcasts/{bid} 용."""
        pattern = f'chat:{self.bid}:*'
        keys = list(self.r.scan_iter(match=pattern, count=500))
        if keys:
            self.r.delete(*keys)
        return len(keys)
