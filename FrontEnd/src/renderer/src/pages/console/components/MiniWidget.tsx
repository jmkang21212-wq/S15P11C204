import { useState } from 'react'
import { formatCount, formatTime } from '@renderer/lib/format'
import type { BriefingType, LiveSnapshot } from '@renderer/types/console'

interface MiniWidgetProps {
  data: LiveSnapshot
  /** HIGH 위험 강조 상태로 렌더 */
  isHigh: boolean
  /** "콘솔로" — 메인 콘솔 창 포커스 */
  onGoConsole: () => void
  /** 미니 창 닫기 */
  onClose: () => void
}

const TYPE_META: Record<BriefingType, { label: string; accent: string }> = {
  RISK: { label: '위험 발언 감지', accent: 'text-[#fb7185]' },
  CHAT_SUMMARY: { label: '채팅 요약', accent: 'text-[#67e8f9]' },
  FAQ: { label: '반복 질문', accent: 'text-[#c4b5fd]' },
  PROMISE: { label: '약속 감지', accent: 'text-[#fcd34d]' }
}

// 프레임 없는 창을 잡아 끌 수 있게 하는 영역 / 그 위 조작 요소는 끌기 제외
const DRAG = '[-webkit-app-region:drag]'
const NO_DRAG = '[-webkit-app-region:no-drag]'

/**
 * 미니 모드 위젯 — 항상-위 별도 창(340×220)의 내용.
 * 창 전체를 채우며, 빈 영역은 드래그로 창을 옮길 수 있다.
 */
export default function MiniWidget({
  data,
  isHigh,
  onGoConsole,
  onClose
}: MiniWidgetProps): React.JSX.Element {
  const briefings = data.briefings
  const total = briefings.length
  const [idx, setIdx] = useState(0)

  const highBriefing = briefings.find((b) => b.briefingType === 'RISK' && b.level === 'HIGH')

  // ── HIGH 경고 시 강조 ──
  if (isHigh && highBriefing) {
    return (
      <div className={`flex h-full w-full flex-col bg-[#1a0e12] p-4 text-[#ffe4e6] ${DRAG}`}>
        <div className="mb-2.5 flex items-center gap-2">
          <span className="flex size-5 items-center justify-center rounded bg-destructive text-xs font-extrabold text-white">
            !
          </span>
          <span className="text-[13px] font-bold text-[#fda4af]">위험 발언 감지 (HIGH)</span>
          <span className="ml-auto font-mono text-[11px] text-[#fb7185]">
            {formatTime(highBriefing.createdAt)}
          </span>
          <button
            type="button"
            onClick={onClose}
            aria-label="닫기"
            className={`text-[#fb7185] ${NO_DRAG}`}
          >
            ✕
          </button>
        </div>
        <div className="flex-1 overflow-y-auto rounded-lg border border-destructive/35 bg-destructive/15 p-3 text-[12.5px] leading-relaxed">
          {highBriefing.message}
        </div>
        <div className="mt-3 flex items-center gap-2">
          <span className="text-[11px] text-[#fda4af]">사운드 알림 재생됨</span>
          <button
            type="button"
            onClick={onGoConsole}
            className={`ml-auto rounded-md bg-destructive px-3 py-1.5 text-[11.5px] font-bold text-white ${NO_DRAG}`}
          >
            콘솔에서 확인 ↗
          </button>
        </div>
      </div>
    )
  }

  const safeIdx = total > 0 ? idx % total : 0
  const current = briefings[safeIdx]
  const meta = current ? TYPE_META[current.briefingType] : null
  const isRisk = current?.briefingType === 'RISK'

  const prev = (): void => setIdx((i) => (i - 1 + total) % total)
  const next = (): void => setIdx((i) => (i + 1) % total)

  return (
    <div className={`flex h-full w-full flex-col bg-[#12151d] p-4 text-[#e6ebf2] ${DRAG}`}>
      {/* 상단: LIVE · 시청자 · 닫기 */}
      <div className="mb-2.5 flex items-center gap-2.5">
        <span className="rounded bg-destructive px-1.5 py-0.5 text-[10px] font-bold text-white">
          ● LIVE
        </span>
        <span className="flex items-center gap-1.5 text-sm font-bold">
          <span className="opacity-70">👁</span>
          <span className="font-mono">{formatCount(data.session.currentViewers)}</span>
        </span>
        <button
          type="button"
          onClick={onClose}
          aria-label="닫기"
          className={`ml-auto text-[#7b8494] hover:text-[#e6ebf2] ${NO_DRAG}`}
        >
          ✕
        </button>
      </div>
      <div className="mb-2.5 flex h-1.5 overflow-hidden rounded-full">
        <div className="bg-success" style={{ width: `${data.sentiment.positiveRatio * 100}%` }} />
        <div className="bg-[#4a5162]" style={{ width: `${data.sentiment.neutralRatio * 100}%` }} />
        <div className="bg-destructive" style={{ width: `${data.sentiment.negativeRatio * 100}%` }} />
      </div>

      {/* 현재 브리핑 카드 (타입별) */}
      <div
        className={`flex-1 overflow-y-auto rounded-lg p-2.5 text-[12px] leading-relaxed ${
          isRisk ? 'bg-destructive/15 ring-1 ring-destructive/40' : 'bg-white/5'
        }`}
      >
        {current && meta && (
          <>
            <div className="mb-1 flex items-center gap-1.5">
              {isRisk && (
                <span className="flex size-4 items-center justify-center rounded bg-destructive text-[9px] font-extrabold text-white">
                  !
                </span>
              )}
              <span className={`text-[10.5px] font-bold ${meta.accent}`}>
                {meta.label}
                {current.level ? ` (${current.level})` : ''}
              </span>
              <span className="ml-auto font-mono text-[10px] text-[#7b8494]">
                {formatTime(current.createdAt)}
              </span>
            </div>
            <span className="text-[#c4cbd6]">{current.message}</span>
          </>
        )}
      </div>

      {/* 하단: ‹ N/M › 넘기기 + 콘솔로 */}
      <div className="mt-2.5 flex items-center gap-2">
        <button
          type="button"
          onClick={prev}
          disabled={total <= 1}
          className={`flex size-6 items-center justify-center rounded-full bg-white/10 text-[#a7b0bf] disabled:opacity-40 ${NO_DRAG}`}
          aria-label="이전 브리핑"
        >
          ‹
        </button>
        <span className="font-mono text-[10.5px] text-[#7b8494]">
          {total > 0 ? safeIdx + 1 : 0}/{total}
        </span>
        <button
          type="button"
          onClick={next}
          disabled={total <= 1}
          className={`flex size-6 items-center justify-center rounded-full bg-white/10 text-[#a7b0bf] disabled:opacity-40 ${NO_DRAG}`}
          aria-label="다음 브리핑"
        >
          ›
        </button>
        <button
          type="button"
          onClick={onGoConsole}
          className={`ml-auto rounded-md bg-[#22d3ee] px-3 py-1.5 text-[11.5px] font-bold text-[#0b0d12] ${NO_DRAG}`}
        >
          콘솔로 ↗
        </button>
      </div>
    </div>
  )
}
