import { useState } from 'react'
import { formatCount, formatPercent, formatTime } from '@renderer/lib/format'
import { topSentiments } from '@renderer/lib/sentiment'
import type { Briefing, BriefingType, LiveSnapshot } from '@renderer/types/console'

interface MiniWidgetProps {
  /** 세션·여론 등 스냅샷 지표. 조회 실패 시 null이며 브리핑은 그대로 보여준다. */
  data: LiveSnapshot | null
  /** 표시할 브리핑 (실시간 + 스냅샷, 최신순) */
  briefings: Briefing[]
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

// 프레임 없는 창을 잡아 끌 수 있게 하는 영역 / 그 위 조작 요소는 끌기 제외
const DRAG = '[-webkit-app-region:drag]'
const NO_DRAG = '[-webkit-app-region:no-drag]'

/**
 * 미니 모드 위젯 — 항상-위 별도 창(340×220)의 내용.
 * 창 전체를 채우며, 빈 영역은 드래그로 창을 옮길 수 있다.
 */
export default function MiniWidget({
  data,
  briefings,
  isHigh,
  onGoConsole,
  onClose
}: MiniWidgetProps): React.JSX.Element {
  const total = MINI_CATEGORIES.length
  const [idx, setIdx] = useState(0)

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
        <div className="flex-1 overflow-y-auto rounded-lg border border-destructive/35 bg-destructive/15 p-3 text-caption leading-relaxed">
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

  const safeIdx = idx % total
  const category = MINI_CATEGORIES[safeIdx]
  const meta = TYPE_META[category]
  // briefings는 최신순이고 실시간이 스냅샷보다 앞에 있어, 첫 일치가 그 카테고리의 최신이다.
  const current = briefings.find((briefing) => briefing.briefingType === category)
  const isRisk = category === 'RISK' && current !== undefined
  const shares = data ? topSentiments(data.sentiment) : []

  const prev = (): void => setIdx((i) => (i - 1 + total) % total)
  const next = (): void => setIdx((i) => (i + 1) % total)

  return (
    <div
      data-surface="overlay"
      className={`flex h-full w-full flex-col bg-card p-4 text-foreground ${DRAG}`}
    >
      {/* 상단: LIVE · 시청자 · 닫기 */}
      <div className="mb-2.5 flex items-center gap-2.5">
        <span className="rounded bg-destructive px-1.5 py-0.5 text-micro font-bold text-white">
          ● LIVE
        </span>
        <span className="flex items-center gap-1.5 text-sm font-bold">
          <span className="opacity-70">👁</span>
          <span className="font-mono">
            {data ? formatCount(data.session.currentViewers) : '-'}
          </span>
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
        className={`flex-1 overflow-y-auto rounded-lg p-2.5 text-caption leading-relaxed ${
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
            {current?.level ? ` (${current.level})` : ''}
          </span>
          {current && (
            <span className="ml-auto font-mono text-micro text-muted-foreground">
              {formatTime(current.createdAt)}
            </span>
          )}
        </div>

        {current ? (
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
