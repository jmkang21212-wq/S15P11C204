"""
LLM 여론 요약 (/summary) — 계약: ../CHAT_AI_API.md 2장

파이프라인 (설계 근거는 HIGHLIGHT_SPEC이 아니라 계약 문서와 팀 논의):
  ① 윈도우 원문 로드 (ingest가 라벨을 붙여 보관해둔 것)
  ② normalize 후 완전일치 dict 카운트 — 도배 압축 (실측: 3분 윈도우의 45~74%가 접힘)
  ③ 리액션 필터 — 순수 챈트(ㅋㅋㅋ 등)는 LLM에 안 보낸다.
     그 정보는 distribution 수치가 이미 전달하므로 이중 지불이다.
  ④ 임계 감정 샘플링 — 비율 ≥ SUMMARY_FOCUS_THRESH 라벨(중립 제외)의
     내용 채팅만. 프론트 감정 칩 표시 기준과 같은 임계값.
  ⑤ LLM 호출 (in-flight 락 + 쿨다운 가드로 비용 상한) → 캐시

가드 3종이 비용의 상한이다:
  채팅 30건 미만 422 · 마지막 요약 후 60초 이내 409 · 호출 진행 중 409
"""
import json
import logging
import re
import time
import uuid
from datetime import datetime, timezone

from .config import (GMS_KEY, LLM_MAX_RETRIES, LLM_TIMEOUT_SEC,
                     SUMMARY_COOLDOWN_SEC, SUMMARY_ENV_MIN, SUMMARY_FOCUS_THRESH,
                     SUMMARY_MIN_CHATS, SUMMARY_NEUTRAL_QUOTA, SUMMARY_SAMPLE_MAX,
                     UID_TTL, WINDOW_SEC)
from .labels import LABELS
from .reaction import is_reaction
from .text_norm import normalize

log = logging.getLogger('chatai.summary')

# in-flight 락 TTL. LLM 최악 지연(timeout × (재시도+1))보다 넉넉해야 한다 —
# 짧으면 호출 도중 락이 풀려 중복 LLM 호출이 일어난다.
# 여유를 두는 이유: 프롬프트 조립·Redis 조회 시간이 LLM 앞뒤로 붙는다.
LOCK_TTL_SEC = LLM_TIMEOUT_SEC * (LLM_MAX_RETRIES + 1) + 15
from .window import BroadcastWindow

# 방송 환경 문제 안내 — 판정도 문장 생성도 전부 코드가 한다.
# nano에게 맡겨본 결과: 예시문을 그대로 베끼고(문제 종류 무시), 칭찬("화면 예쁘다")을
# 문제로 오인했다. 키워드 매칭 + 감정 라벨 필터로 판정하고, 템플릿 문장을 붙인다.
_POSITIVE = {'환호/감탄', '응원/호감', '웃음/재미'}   # 이 라벨이면 칭찬 — 불만 아님
_ENV_CATS = [
    ('소리', re.compile('소리|볼륨|마이크|음향|음성|뮤트'),
     '아, 소리 관련 채팅이 꽤 보여요. 마이크나 볼륨 상태 한번 확인해보시면 좋을 것 같아요!'),
    ('화면', re.compile('화면|끊[겨기김켜]|버퍼|렉|랙|프리즈|멈췄|멈춤|송출|화질|딜레이'),
     '아, 화면이 끊기거나 멈춘다는 채팅이 꽤 보여요. 송출 상태 한번 확인해보시면 좋을 것 같아요!'),
    ('자막', re.compile('자막|싱크'),
     '아, 자막이나 싱크 얘기가 보여요. 설정 한번 확인해보시면 좋을 것 같아요!'),
]


def env_notice(groups):
    """환경 문제 안내 문장 (없으면 None). groups: 중복 압축된 채팅 목록."""
    best = None
    for name, pattern, template in _ENV_CATS:
        n = sum(g['n'] for g in groups
                if g['label'] not in _POSITIVE and pattern.search(g['text']))
        if n >= SUMMARY_ENV_MIN and (best is None or n > best[0]):
            best = (n, template)
    return best[1] if best else None


def _iso(ms):
    return (datetime.fromtimestamp(ms / 1000, timezone.utc)
            .isoformat(timespec='seconds').replace('+00:00', 'Z'))


class SummaryRejected(Exception):
    """가드 거절 — server.py가 status/payload를 그대로 HTTP 응답으로 변환한다."""

    def __init__(self, status, payload):
        self.status = status
        self.payload = payload
        super().__init__(payload.get('reason', ''))


class SummaryService:
    """llm_fn: (prompt: str) -> str. 테스트에선 가짜 함수를 주입한다."""

    def __init__(self, r, llm_fn):
        self.r = r
        self.llm_fn = llm_fn

    # ---------------------------------------------------------------- 공개 API

    def get_cached(self, bid):
        raw = self.r.get(f'chat:{bid}:sum')
        return json.loads(raw) if raw else None

    def generate(self, bid, mode='AUTO', now_ms=None):
        now_ms = int(now_ms if now_ms is not None else time.time() * 1000)
        win = BroadcastWindow(self.r, bid)

        if not win.exists():
            raise SummaryRejected(404, {'reason': 'UNKNOWN_BROADCAST'})
        if self.llm_fn is None:
            raise SummaryRejected(503, {'reason': 'LLM_UNCONFIGURED',
                                        'detail': 'GMS_KEY가 설정되지 않았습니다'})

        # in-flight 락 (방송별) — LLM 호출 중 중복 요청 차단.
        # 값에 고유 토큰을 넣는 이유: 무조건 delete 하면 TTL 만료 후 다른 요청이
        # 새로 잡은 락을 내가 지워버린다. 해제 시 내 것인지 확인하고 지운다.
        lock_key = f'chat:{bid}:lock'
        token = uuid.uuid4().hex
        if not self.r.set(lock_key, token, nx=True, ex=LOCK_TTL_SEC):
            raise SummaryRejected(409, {'reason': 'IN_FLIGHT'})

        try:
            # 쿨다운 — 기준은 캐시된 마지막 요약의 generatedAt
            cached = self.get_cached(bid)
            if cached:
                last_ms = cached.get('_generatedAtMs', 0)
                elapsed = (now_ms - last_ms) / 1000
                if elapsed < SUMMARY_COOLDOWN_SEC:
                    raise SummaryRejected(409, {
                        'reason': 'COOLDOWN',
                        'retryAfterSec': int(SUMMARY_COOLDOWN_SEC - elapsed),
                        'lastSummaryAt': cached['generatedAt'],
                    })

            texts = win.recent_texts(now_ms)
            if len(texts) < SUMMARY_MIN_CHATS:
                raise SummaryRejected(422, {
                    'reason': 'NOT_ENOUGH_CHATS',
                    'chatCount': len(texts),
                    'required': SUMMARY_MIN_CHATS,
                })

            metrics = win.metrics(now_ms)
            prompt = self._build_prompt(texts, metrics)
            t0 = time.perf_counter()
            summary_text = self.llm_fn(prompt)
            # LLM 지연은 우리 코드가 아니라 GMS 사정이라 따로 남긴다 —
            # "요약이 느리다"가 추론 문제인지 외부 문제인지 이 줄로 갈린다
            log.info('LLM 요약 bid=%s %.1fs 입력=%d자 채팅=%d건',
                     bid, time.perf_counter() - t0, len(prompt), len(texts))

            # 환경 문제 안내 — LLM에 안 맡긴다 (판정: 키워드+라벨, 문장: 템플릿).
            # nano가 예시문 복사·칭찬 오인을 반복해 코드로 확정 (2026-07-29 검증)
            notice = env_notice(self._group(texts))
            if notice:
                summary_text = summary_text.rstrip() + ' ' + notice

            result = {
                'summary': summary_text,
                'dominant': metrics['dominant'],
                'distribution': metrics['distribution'],
                'chatCount': metrics['chatCount'],
                'windowSec': WINDOW_SEC,
                'generatedAt': _iso(now_ms),
                'nextAvailableAt': _iso(now_ms + SUMMARY_COOLDOWN_SEC * 1000),
                '_generatedAtMs': now_ms,        # 쿨다운 계산용 내부 필드
            }
            self.r.set(f'chat:{bid}:sum', json.dumps(result, ensure_ascii=False),
                       ex=UID_TTL)
            return {k: v for k, v in result.items() if not k.startswith('_')}
        finally:
            self._release(lock_key, token, bid)

    # ------------------------------------------------------------------ 락 해제

    # 내 토큰일 때만 삭제 (compare-and-delete). Redis에서 원자 실행되므로
    # "확인하고 지우는 사이에 만료되어 남의 락을 지우는" 경합이 생기지 않는다.
    _RELEASE_LUA = """
    if redis.call('get', KEYS[1]) == ARGV[1] then
        return redis.call('del', KEYS[1])
    end
    return 0
    """

    def _release(self, lock_key, token, bid):
        try:
            freed = self.r.eval(self._RELEASE_LUA, 1, lock_key, token)
        except Exception:
            # 해제 실패는 치명적이지 않다 — TTL이 지워준다. 요청 자체는 성공시킨다
            log.warning('락 해제 실패 bid=%s — TTL(%d초)로 자동 해제됨', bid, LOCK_TTL_SEC)
            return
        if not freed:
            # 내 락이 이미 없다 = LLM이 LOCK_TTL_SEC를 넘겼고 그 사이 다른 요청이
            # 락을 새로 잡았다는 뜻. 중복 LLM 호출이 일어났을 수 있다.
            log.warning('락이 만료된 뒤 완료됨 bid=%s — LLM이 %d초를 초과했다. '
                        'CHATAI_LLM_TIMEOUT/LOCK TTL을 확인할 것', bid, LOCK_TTL_SEC)

    # ------------------------------------------------------------ 샘플링·프롬프트

    @staticmethod
    def _group(texts):
        """normalize 후 완전일치 카운트 — 도배 압축 (실측 중앙값 59%)."""
        groups = {}
        for it in texts:
            key = normalize(it['m'])
            if not key:
                continue                    # 이모티콘만 있던 채팅
            g = groups.setdefault(key, {'text': key, 'n': 0, 'label': it['l'],
                                        't': it['t']})
            g['n'] += 1
        return list(groups.values())

    def _build_prompt(self, texts, metrics):
        dist = metrics['distribution']
        groups = self._group(texts)

        # 리액션/내용 분리 — 챈트는 규모 요약 한 줄로만
        reactions, content = [], []
        for g in groups:
            (reactions if is_reaction(g['text']) else content).append(g)
        top_chants = sorted(reactions, key=lambda g: -g['n'])[:3]

        # 임계 라벨 (중립 제외 — 구조적 1위라 필터가 안 됨)
        focus = [lab for lab in LABELS
                 if lab != '중립' and dist.get(lab, 0) >= SUMMARY_FOCUS_THRESH]
        if not focus:                       # 조용한 방송 폴백: 중립 제외 상위 2개
            focus = sorted((lab for lab in LABELS if lab != '중립'),
                           key=lambda lab: -dist.get(lab, 0))[:2]

        picked = self._pick(content, focus, dist)

        # 프롬프트 조립 — 서술 대상은 임계 라벨만. 미달 감정 수치를 보여주면
        # LLM이 성실하게 전부 언급해버린다 ("소수의 분노·슬픔은…" 류 군더더기)
        # 환경 문제 안내는 여기 없다 — env_notice()가 코드로 판정·생성해 뒤에 붙인다
        focus_line = ' · '.join(f'{lab} {dist[lab]:.0%}' for lab in focus)
        chant_line = ' · '.join(f"{g['text']}(×{g['n']})" for g in top_chants)
        chat_lines = '\n'.join(
            f"- {g['text']}" + (f" (×{g['n']})" if g['n'] > 1 else '')
            for g in picked)

        return f"""너는 스트리머 곁의 친근한 AI 매니저다. 방송 중인 스트리머에게
최근 3분 채팅 여론을 짧게 브리핑한다. (전체 {metrics['chatCount']}건)

서술 대상 감정 — 이것만 다뤄라:
{focus_line}
(다른 감정들은 비중이 작아 화면에 표시되지 않는다. 언급 금지. 중립 잡담도 서술 대상이 아니다.)

대표 반응: {chant_line or '(없음)'}

내용 채팅 표본 (반응의 원인을 여기서 찾아라 · ×N은 반복 횟수):
{chat_lines or '(내용 채팅 없음)'}

[출력 형식 — 정확히 지켜라]
한 단락으로만 출력한다. 머리말·제목·빈 줄 없이 첫 글자부터 브리핑이다.
브리핑은 최대 3문장 (친근한 해요체 — 옆에서 말해주듯). 각 문장은 원인을 먼저,
반응을 나중에. 예시 말투: "~하는 장면에 웃음이 터지고 있어요" (내용은 예시와 무관)
총 문장 수는 어떤 경우에도 3문장을 넘지 않는다.

[금지]
- 머리말·제목·영어 단어 · 위 지침 문구를 출력에 베끼는 것
- 같은 내용을 두 문장으로 반복하는 것
- 채팅에 없는 내용 추측 · 채팅 문구 따옴표 나열 · 기호나 화살표 사용
- 감정 라벨명(웃음/재미, 응원/호감 등)과 퍼센트 수치를 문장에 그대로 쓰는 것
  — 자연어로 풀어 써라 (예: "웃음이 터지고 있어요", "응원이 이어지고 있어요")
- 딱딱한 보고서 말투(…이다, …함)와 "권고:" 같은 접두사
- 스트리머에게 조언·권고·요청을 쓰는 것 — 브리핑만 하라 (안내는 시스템이 따로 붙인다)"""

    @staticmethod
    def _pick(content, focus, dist):
        """임계 라벨 내용 채팅을 라벨 비율대로 배분 + 중립 맥락 소량. 시간순 반환."""
        picked = []

        focus_total = sum(dist.get(lab, 0) for lab in focus) or 1.0
        budget = SUMMARY_SAMPLE_MAX - SUMMARY_NEUTRAL_QUOTA
        for lab in focus:
            pool = [g for g in content if g['label'] == lab]
            quota = max(5, int(budget * dist.get(lab, 0) / focus_total))
            if len(pool) <= quota:
                picked += pool
                continue
            # 카운트 상위 절반 + 나머지는 시간 균등 스트라이드
            by_count = sorted(pool, key=lambda g: -g['n'])
            head = by_count[:quota // 2]
            head_ids = {id(g) for g in head}
            remaining = [g for g in pool if id(g) not in head_ids]
            step = max(1, len(remaining) // max(1, quota - len(head)))
            picked += head + remaining[::step][:quota - len(head)]

        if SUMMARY_NEUTRAL_QUOTA:
            neutral = [g for g in content if g['label'] == '중립']
            neutral.sort(key=lambda g: (-g['n'], -len(g['text'])))
            picked += neutral[:SUMMARY_NEUTRAL_QUOTA]

        picked.sort(key=lambda g: g['t'])
        return picked[:SUMMARY_SAMPLE_MAX]
