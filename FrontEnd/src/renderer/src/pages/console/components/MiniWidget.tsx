import { useEffect, useRef, useState } from 'react'
import { formatCount, formatPercent, formatTime } from '@renderer/lib/format'
import { topSentiments } from '@renderer/lib/sentiment'
import type {
  Briefing,
  BriefingType,
  LiveSnapshot,
  RepeatedQuestion
} from '@renderer/types/console'

interface MiniWidgetProps {
  /** 세션·여론 등 스냅샷 지표. 조회 실패 시 null이며 브리핑은 그대로 보여준다. */
  data: LiveSnapshot | null
  /** 표시할 브리핑 (실시간 + 스냅샷, 최신순) */
  briefings: Briefing[]
  /** FAQ 칸에 쓸 반복 질문 (askers 내림차순, 호출부에서 이미 필터링됨) */
  questions: RepeatedQuestion[]
  /** 반복 질문 답변 완료 — 콘솔 창에 처리를 요청한다 */
  onDismissQuestion: (questionId: number) => void
  /** HIGH 위험 강조 상태로 렌더 */
  isHigh: boolean
  /** "콘솔로" — 메인 콘솔 창 포커스 */
  onGoConsole: () => void
  /** 미니 창 닫기 */
  onClose: () => void
}

const TYPE_META: Record<BriefingType, { label: string; accent: string }> = {
  RISK: { label: '위험 발언 감지', accent: 'text-briefing-risk' },
  CHAT_SUMMARY: { label: '채팅 요약', accent: 'text-briefing-chat' },
  FAQ: { label: '반복 질문', accent: 'text-briefing-faq' },
  PROMISE: { label: '약속 감지', accent: 'text-briefing-promise' }
}

/**
 * 미니 모드는 카테고리 4칸을 고정해두고 각 칸에 그 카테고리의 최신 알림만 보여준다.
 * 좁은 창에서 이벤트를 계속 누적하면 방금 무슨 일이 있었는지 파악하기 어렵다.
 * 상세 이력은 콘솔의 브리핑 피드가 담당한다.
 */
const MINI_CATEGORIES: BriefingType[] = ['RISK', 'CHAT_SUMMARY', 'FAQ', 'PROMISE']

/**
 * 칸이 한 번 바뀐 뒤 이 시간 동안은 다시 바꾸지 않는다.
 *
 * 자동 전환과 사용자의 화살표 조작에 똑같이 걸린다. 알림이 몰려 오면 마지막 것만 남고
 * 앞의 것은 읽지도 못한 채 지나가고, 방금 직접 넘긴 칸을 곧바로 빼앗기면 넘긴 의미가 없다.
 */
const SWITCH_LOCK_MS = 3000

/** 한 칸에 지금 보이는 내용의 지문. */
interface SlotSignal {
  /** 내용 식별자. 빈 칸이면 '' */
  key: string
  /** 그 내용이 생긴 시각(epoch ms). 빈 칸이면 0 */
  at: number
}

const EMPTY_SIGNAL: SlotSignal = { key: '', at: 0 }

/**
 * 네 칸 각각의 "지금 보이는 내용"을 지문으로 만든다. 자동 전환은 이 지문이 바뀐 칸을 찾는다.
 *
 * <b>화면에 실제로 보이는 것만 지문에 넣는다.</b> 반복 질문 칸은 목록 전체가 아니라 1위
 * 질문 하나만 보여주므로 1위가 교체될 때만 갱신으로 친다 — 2위 이하에 새로 물은 사람이
 * 생겨도 보이는 내용은 그대로라, 그걸로 전환하면 "넘어갔는데 화면은 같은" 상태가 된다.
 */
function slotSignals(briefings: Briefing[], questions: RepeatedQuestion[]): SlotSignal[] {
  return MINI_CATEGORIES.map((category) => {
    if (category === 'FAQ') {
      const top = questions[0]
      return top
        ? { key: `q${top.questionId}`, at: Date.parse(top.lastAskedAt) || 0 }
        : EMPTY_SIGNAL
    }
    // briefings는 최신순이라 첫 일치가 그 카테고리의 최신이다.
    const latest = briefings.find((briefing) => briefing.briefingType === category)
    return latest
      ? { key: `b${latest.briefingId}`, at: Date.parse(latest.createdAt) || 0 }
      : EMPTY_SIGNAL
  })
}

/** 내용이 있는 칸 중 가장 최근 것. 전부 비었으면 첫 칸에 둔다. */
function newestSlot(signals: SlotSignal[]): number {
  let best = -1
  signals.forEach((signal, index) => {
    if (signal.key === '') return
    if (best === -1 || signal.at > signals[best].at) best = index
  })
  return best === -1 ? 0 : best
}

/**
 * 직전 지문과 비교해 새로 갱신된 칸을 고른다. 여럿이 한꺼번에 바뀌었으면 가장 최근 것.
 *
 * 빈 칸은 대상이 아니다 — 내용이 사라진 자리로 넘어가면 보여줄 것이 없다. 사용자가
 * 화살표로 직접 가는 것은 그대로 되고, "자동으로 데려가지 않을" 뿐이다.
 */
function updatedSlot(previous: string[], signals: SlotSignal[]): number | null {
  let best = -1
  signals.forEach((signal, index) => {
    if (signal.key === '' || signal.key === previous[index]) return
    if (best === -1 || signal.at > signals[best].at) best = index
  })
  return best === -1 ? null : best
}

/**
 * 자동 전환 상태.
 *
 * 잠금 해제 시각은 여기 두지 않고 ref로 뺐다 — 렌더 중에 `Date.now()`를 읽으면
 * 컴포넌트가 순수하지 않게 되고(react-hooks/purity), 실제로 시각이 필요한 곳은
 * 타이머를 거는 effect뿐이다.
 */
interface SwitchState {
  /** 마지막으로 관찰한 네 칸의 지문 */
  keys: string[]
  /** 지금 보여주는 칸 */
  shown: number
  /** 넘어갈 차례를 기다리는 칸. 잠금이 없으면 곧바로 반영된다. */
  pending: number | null
}

// 프레임 없는 창을 잡아 끌 수 있게 하는 영역 / 그 위 조작 요소는 끌기 제외
const DRAG = '[-webkit-app-region:drag]'
const NO_DRAG = '[-webkit-app-region:no-drag]'

/**
 * 미니 모드 위젯 — 항상-위 별도 창(340×220)의 내용.
 * 창 전체를 채우며, 빈 영역은 드래그로 창을 옮길 수 있다.
 *
 * <b>새 내용이 도착한 칸으로 자동으로 넘어간다.</b> 이 창을 띄우는 상황은 콘솔을 볼 수
 * 없을 때인데, 갱신을 확인하려고 화살표를 눌러야 하면 결국 창을 주시해야 한다.
 * 다만 바뀐 직후 {@link SWITCH_LOCK_MS} 동안은 잠가서, 알림이 몰려도 읽을 시간을 준다.
 * 잠금 중에 온 갱신은 버리지 않고 잠금이 풀릴 때 반영한다.
 *
 * HIGH 위험은 이 흐름을 타지 않는다 — 아래 전용 강조 화면이 창 전체를 덮는다.
 */
export default function MiniWidget({
  data,
  briefings,
  questions,
  onDismissQuestion,
  isHigh,
  onGoConsole,
  onClose
}: MiniWidgetProps): React.JSX.Element {
  const total = MINI_CATEGORIES.length
  const signals = slotSignals(briefings, questions)
  const keys = signals.map((signal) => signal.key)

  /**
   * 처음 열 때부터 가장 최신 칸을 보여준다 — 예전처럼 늘 첫 칸(위험 발언)에서
   * 시작하면 창을 열자마자 빈 칸을 보는 일이 잦다.
   */
  const [auto, setAuto] = useState<SwitchState>(() => ({
    keys,
    shown: newestSlot(signals),
    pending: null
  }))
  /** 이 시각(epoch ms)까지는 칸을 바꾸지 않는다. 렌더가 아니라 타이머·핸들러만 읽는다. */
  const lockedUntil = useRef(0)

  /**
   * 새 내용이 도착하면 그 칸으로 옮긴다.
   *
   * effect가 아니라 렌더 중 조정으로 처리한다. "props가 바뀌면 state를 맞춘다"는 React가
   * 권하는 패턴이기도 하고, effect 본문에서 setState를 부르면 react-hooks의
   * set-state-in-effect에 걸린다(useLiveSnapshot도 같은 이유로 이 형태를 쓴다).
   */
  if (keys.some((key, index) => key !== auto.keys[index])) {
    // 이미 보고 있는 칸이 갱신된 것이면 옮길 필요가 없다. 앞서 예약된 칸이 있으면 살려 둔다.
    const target = updatedSlot(auto.keys, signals)
    const nextPending = target === null || target === auto.shown ? auto.pending : target
    setAuto({ ...auto, keys, pending: nextPending })
  }

  /**
   * 예약된 칸으로 실제로 넘어간다.
   *
   * 잠금이 없으면 대기 시간이 0이라 사실상 즉시 넘어가고, 잠금 중이면 풀리는 순간에
   * 넘어간다 — 두 경우를 한 경로로 처리한다. `Date.now()`가 렌더가 아니라 여기서만
   * 불리는 것도 이 구조를 택한 이유다.
   */
  useEffect(() => {
    if (auto.pending === null) return
    const timer = window.setTimeout(
      () => {
        lockedUntil.current = Date.now() + SWITCH_LOCK_MS
        setAuto((current) =>
          current.pending === null ? current : { ...current, shown: current.pending, pending: null }
        )
      },
      Math.max(0, lockedUntil.current - Date.now())
    )
    return () => window.clearTimeout(timer)
  }, [auto.pending])

  const highBriefing = briefings.find((b) => b.briefingType === 'RISK' && b.level === 'HIGH')

  // ── HIGH 경고 시 강조 ──
  if (isHigh && highBriefing) {
    return (
      <div
        data-surface="overlay-alert"
        className={`flex h-full w-full flex-col bg-card p-4 text-foreground ${DRAG}`}
      >
        <div className="mb-2.5 flex items-center gap-2">
          <span className="flex size-5 items-center justify-center rounded bg-destructive text-xs font-extrabold text-white">
            !
          </span>
          <span className="text-body font-bold text-muted-foreground">위험 발언 감지 (HIGH)</span>
          <span className="ml-auto font-mono text-caption text-accent-foreground">
            {formatTime(highBriefing.createdAt)}
          </span>
          <button
            type="button"
            onClick={onClose}
            aria-label="닫기"
            className={`text-accent-foreground ${NO_DRAG}`}
          >
            ✕
          </button>
        </div>
        <div
          className={`flex-1 overflow-y-auto rounded-lg border border-destructive/35 bg-destructive/15 p-3 text-caption leading-relaxed scrollbar-none [&::-webkit-scrollbar]:hidden ${NO_DRAG}`}
        >
          {highBriefing.message}
          {highBriefing.quote && (
            <span className="mt-1 block text-muted-foreground">“{highBriefing.quote}”</span>
          )}
        </div>
        <div className="mt-3 flex items-center gap-2">
          <span className="text-caption text-muted-foreground">사운드 알림 재생됨</span>
          <button
            type="button"
            onClick={onGoConsole}
            className={`ml-auto rounded-md bg-destructive px-3 py-1.5 text-caption font-bold text-white ${NO_DRAG}`}
          >
            콘솔에서 확인 ↗
          </button>
        </div>
      </div>
    )
  }

  const safeIdx = auto.shown % total
  const category = MINI_CATEGORIES[safeIdx]
  const meta = TYPE_META[category]
  // briefings는 최신순이고 실시간이 스냅샷보다 앞에 있어, 첫 일치가 그 카테고리의 최신이다.
  const current = briefings.find((briefing) => briefing.briefingType === category)
  const isRisk = category === 'RISK' && current !== undefined
  const shares = data ? topSentiments(data.sentiment) : []

  // FAQ 칸은 브리핑이 아니라 반복 질문 목록에서 온다 (스냅샷의 별도 배열).
  // 가장 많이 물은 것 하나만 두고, 답하면 다음 질문이 그 자리에 올라온다.
  const isFaqSlot = category === 'FAQ'
  const topQuestion = questions[0]
  const remainingQuestions = Math.max(0, questions.length - 1)

  // 직접 넘긴 칸에도 같은 잠금을 건다. 예약은 버린다 — 방금 고른 칸을 3초 뒤에
  // 빼앗으면 직접 넘긴 의미가 없다. 놓친 알림은 그 칸에 그대로 남아 있다.
  const goTo = (index: number): void => {
    lockedUntil.current = Date.now() + SWITCH_LOCK_MS
    setAuto((current) => ({ ...current, shown: index, pending: null }))
  }
  const prev = (): void => goTo((safeIdx - 1 + total) % total)
  const next = (): void => goTo((safeIdx + 1) % total)

  return (
    <div
      data-surface="overlay"
      className={`flex h-full w-full flex-col bg-card p-4 text-foreground ${DRAG}`}
    >
      {/* 상단: LIVE · 시청자 · 닫기 */}
      <div className="mb-2.5 flex items-center gap-2.5">
        {data?.session.status === 'LIVE' && (
          <span className="rounded bg-destructive px-1.5 py-0.5 text-micro font-bold text-white">
            ● LIVE
          </span>
        )}
        <span className="flex items-center gap-1.5 text-sm font-bold">
          <span className="opacity-70">👁</span>
          <span className="font-mono">{data ? formatCount(data.session.currentViewers) : '-'}</span>
        </span>
        <button
          type="button"
          onClick={onClose}
          aria-label="닫기"
          className={`ml-auto text-muted-foreground hover:text-foreground ${NO_DRAG}`}
        >
          ✕
        </button>
      </div>
      {/* 여론은 상위 3종 막대 + 최다 감정 한 줄만 — 상세는 콘솔의 몫이다 */}
      {shares.length > 0 && (
        <div className="mb-2.5">
          <div className="mb-1 flex h-1.5 overflow-hidden rounded-full bg-white/10">
            {shares.map((share) => (
              <span
                key={share.label}
                className={share.barClass}
                style={{ width: `${share.ratio * 100}%` }}
              />
            ))}
          </div>
          <span className="text-micro text-muted-foreground">
            {shares[0].text} {formatPercent(shares[0].ratio)}
          </span>
        </div>
      )}

      {/* 현재 브리핑 카드 (타입별) */}
      <div
        className={`flex-1 overflow-y-auto rounded-lg p-2.5 text-caption leading-relaxed scrollbar-none [&::-webkit-scrollbar]:hidden ${NO_DRAG} ${
          isRisk ? 'bg-destructive/15 ring-1 ring-destructive/40' : 'bg-white/5'
        }`}
      >
        {/* 카테고리 헤더는 알림이 없어도 고정으로 보여준다 */}
        <div className="mb-1 flex items-center gap-1.5">
          {isRisk && (
            <span className="flex size-4 items-center justify-center rounded bg-destructive text-micro font-extrabold text-white">
              !
            </span>
          )}
          <span className={`text-micro font-bold ${meta.accent}`}>
            {meta.label}
            {!isFaqSlot && current?.level ? ` (${current.level})` : ''}
          </span>
          {!isFaqSlot && current && (
            <span className="ml-auto font-mono text-micro text-muted-foreground">
              {formatTime(current.createdAt)}
            </span>
          )}
          {isFaqSlot && topQuestion && (
            <span className="ml-auto font-mono text-micro text-muted-foreground">
              {formatCount(topQuestion.askers)}명
            </span>
          )}
        </div>

        {isFaqSlot ? (
          topQuestion ? (
            <>
              <span className="text-secondary-foreground">{topQuestion.text}</span>
              <div className={`mt-2 flex items-center gap-2 ${NO_DRAG}`}>
                {remainingQuestions > 0 && (
                  <span className="text-micro text-muted-foreground">
                    {remainingQuestions}건 더
                  </span>
                )}
                <button
                  type="button"
                  onClick={() => onDismissQuestion(topQuestion.questionId)}
                  className="ml-auto rounded-md bg-white/10 px-2.5 py-1 text-micro font-bold text-secondary-foreground"
                >
                  답변 완료
                </button>
              </div>
            </>
          ) : (
            <span className="text-muted-foreground">아직 반복되는 질문이 없어요</span>
          )
        ) : current ? (
          <>
            <span className="text-secondary-foreground">{current.message}</span>
            {current.quote && (
              <span className="mt-1 block text-muted-foreground">“{current.quote}”</span>
            )}
          </>
        ) : (
          <span className="text-muted-foreground">아직 도착한 알림이 없어요</span>
        )}
      </div>

      {/* 하단: ‹ N/M › 넘기기 + 콘솔로 */}
      <div className="mt-2.5 flex items-center gap-2">
        <button
          type="button"
          onClick={prev}
          className={`flex size-6 items-center justify-center rounded-full bg-white/10 text-overlay-icon ${NO_DRAG}`}
          aria-label="이전 카테고리"
        >
          ‹
        </button>
        <span className="font-mono text-micro text-muted-foreground">
          {safeIdx + 1}/{total}
        </span>
        <button
          type="button"
          onClick={next}
          className={`flex size-6 items-center justify-center rounded-full bg-white/10 text-overlay-icon ${NO_DRAG}`}
          aria-label="다음 카테고리"
        >
          ›
        </button>
        <button
          type="button"
          onClick={onGoConsole}
          className={`ml-auto rounded-md bg-primary px-3 py-1.5 text-caption font-bold text-primary-foreground ${NO_DRAG}`}
        >
          콘솔로 ↗
        </button>
      </div>
    </div>
  )
}
