import { useState } from 'react'
import { Link } from 'react-router-dom'
import { formatCount, formatTime } from '@renderer/lib/format'
import { PATHS } from '@renderer/routes/paths'
import type { BriefingType, LiveSnapshot } from '@renderer/types/console'

interface MiniWidgetProps {
  data: LiveSnapshot
  /** HIGH 위험 강조(확대) 상태로 렌더 — 목업의 "HIGH 경고 시 확대·강조" */
  isHigh: boolean
}

const TYPE_META: Record<BriefingType, { label: string; accent: string }> = {
  RISK: { label: '위험 발언 감지', accent: 'text-[#fb7185]' },
  CHAT_SUMMARY: { label: '채팅 요약', accent: 'text-[#67e8f9]' },
  FAQ: { label: '반복 질문', accent: 'text-[#c4b5fd]' },
  PROMISE: { label: '약속 감지', accent: 'text-[#fcd34d]' },
}

/**
 * 미니 모드 위젯 본체.
 * 기본 상태: 브리핑을 타입별 카드로 보여주고 ‹ › 로 직접 넘겨가며 확인한다.
 * HIGH 상태: 위험 발언을 확대·강조(별도 변형).
 * 라우트 미리보기(MiniModePage)와 콘솔 내 인앱 토글에서 공용으로 쓴다.
 */
export default function MiniWidget({ data, isHigh }: MiniWidgetProps): React.JSX.Element {
  const briefings = data.briefings
  const total = briefings.length
  const [idx, setIdx] = useState(0)

  const highBriefing = briefings.find((b) => b.briefingType === 'RISK' && b.level === 'HIGH')

  // ── HIGH 경고 시 확대·강조 ──
  if (isHigh && highBriefing) {
    return (
      <div className="flex h-[200px] w-[340px] flex-col rounded-xl border-2 border-destructive bg-[#1a0e12] p-4 text-[#ffe4e6] shadow-[0_0_0_3px_rgba(225,29,72,.25)]">
        <div className="mb-2.5 flex items-center gap-2">
          <span className="flex size-5 items-center justify-center rounded bg-destructive text-xs font-extrabold text-white">
            !
          </span>
          <span className="text-[13px] font-bold text-[#fda4af]">위험 발언 감지 (HIGH)</span>
          <span className="ml-auto font-mono text-[11px] text-[#fb7185]">
            {formatTime(highBriefing.createdAt)}
          </span>
        </div>
        <div className="flex-1 rounded-lg border border-destructive/35 bg-destructive/15 p-3 text-[12.5px] leading-relaxed">
          {highBriefing.message}
        </div>
        <div className="mt-3 flex items-center gap-2">
          <span className="text-[11px] text-[#fda4af]">사운드 알림 재생됨</span>
          <Link
            to={PATHS.console}
            className="ml-auto rounded-md bg-destructive px-3 py-1.5 text-[11.5px] font-bold text-white"
          >
            콘솔에서 확인 ↗
          </Link>
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
    <div className="flex h-[220px] w-[340px] flex-col rounded-xl bg-[#12151d] p-4 text-[#e6ebf2]">
      {/* 상단: LIVE · 시청자 · 여론 (항상 표시) */}
      <div className="mb-2.5 flex items-center gap-2.5">
        <span className="rounded bg-destructive px-1.5 py-0.5 text-[10px] font-bold text-white">
          ● LIVE
        </span>
        <span className="flex items-center gap-1.5 text-sm font-bold">
          <span className="opacity-70">👁</span>
          <span className="font-mono">{formatCount(data.session.currentViewers)}</span>
        </span>
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
          className="flex size-6 items-center justify-center rounded-full bg-white/10 text-[#a7b0bf] disabled:opacity-40"
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
          className="flex size-6 items-center justify-center rounded-full bg-white/10 text-[#a7b0bf] disabled:opacity-40"
          aria-label="다음 브리핑"
        >
          ›
        </button>
        <Link
          to={PATHS.console}
          className="ml-auto rounded-md bg-[#22d3ee] px-3 py-1.5 text-[11.5px] font-bold text-[#0b0d12]"
        >
          콘솔로 ↗
        </Link>
      </div>
    </div>
  )
}
