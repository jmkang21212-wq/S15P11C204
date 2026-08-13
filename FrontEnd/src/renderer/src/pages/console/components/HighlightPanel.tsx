import { memo } from 'react'
import StatusBadge, { type StatusTone } from '@renderer/components/common/StatusBadge'
import { formatTime } from '@renderer/lib/format'
import { cn } from '@renderer/lib/utils'
import type { HighlightStatus } from '@renderer/types/console'

/**
 * 콘솔 하이라이트 감지 패널 — CHAT_SURGE 판정기의 현재 상태를 보여준다.
 *
 * 판정 파라미터가 백테스트 미검증이라(백엔드 HIGHLIGHT_SPEC §5) 시연·튜닝 때
 * "지금 z가 얼마고 왜 안 잡히는지"가 화면에 보여야 한다는 것이 존재 이유다.
 * z 게이지는 진입 임계(zEnter) 대비 현재 z의 비율 — 바가 끝까지 차오른 상태가
 * 몇 초 이어지면 하이라이트 진입이다.
 */

const PHASE_BADGE: Record<'IDLE' | 'ACTIVE' | 'COOLDOWN', { tone: StatusTone; label: string }> = {
  IDLE: { tone: 'neutral', label: '대기' },
  ACTIVE: { tone: 'danger', label: '감지 중' },
  COOLDOWN: { tone: 'warning', label: '쿨다운' }
}

/** 판정을 쉬는 이유(가드) → 사람이 읽는 문구. 백엔드 skipReason 값과 짝이다. */
const SKIP_LABEL: Record<NonNullable<HighlightStatus['skipReason']>, string> = {
  warmup: '워밍업 — 방송 초반에는 판정하지 않아요',
  'no-viewers': '시청자 수가 서버에 안 들어와요 — 수집기 폴링을 확인하세요',
  'zero-viewers': '시청자 0명 — 실제 송출과 시청자가 있어야 판정해요',
  quiet: '채팅이 적어 판정을 쉬고 있어요',
  gap: '채팅 수집 공백 — 이 구간은 판정에서 제외돼요',
  'empty-baseline': '평소 수준(기준선) 수집 중이에요'
}

function HighlightPanel({ highlight }: { highlight: HighlightStatus }): React.JSX.Element {
  const badge = highlight.phase ? PHASE_BADGE[highlight.phase] : null
  // 게이지는 임계 대비 비율. z는 음수도 나오므로(평소보다 조용) 0~1로 자른다.
  const gaugeRatio =
    highlight.z !== null && highlight.zEnter
      ? Math.min(Math.max(highlight.z / highlight.zEnter, 0), 1)
      : null

  return (
    <div>
      <div className="mb-3 flex items-baseline justify-between">
        <span className="text-xs font-semibold text-muted-foreground">
          하이라이트 감지{' '}
          {highlight.engageWindowSec !== null && (
            <span className="font-normal text-muted-foreground/70">
              · 최근 {highlight.engageWindowSec}초
            </span>
          )}
        </span>
        {badge ? (
          <StatusBadge tone={badge.tone} label={badge.label} />
        ) : (
          <StatusBadge tone="neutral" label="판정 준비 중" />
        )}
      </div>

      {gaugeRatio !== null ? (
        <div className="flex items-center gap-2.5">
          <span className="h-2 flex-1 overflow-hidden rounded-full bg-muted">
            <span
              className={cn(
                'block h-full rounded-full transition-[width] duration-500',
                highlight.phase === 'ACTIVE' ? 'bg-destructive' : 'bg-primary'
              )}
              style={{ width: `${gaugeRatio * 100}%` }}
            />
          </span>
          <span className="shrink-0 font-mono text-caption text-muted-foreground">
            z {highlight.z!.toFixed(1)}
            <span className="text-muted-foreground/60"> / {highlight.zEnter!.toFixed(1)}</span>
          </span>
        </div>
      ) : (
        <p className="rounded-lg bg-muted/40 px-3 py-3 text-center text-caption text-muted-foreground">
          {highlight.skipReason
            ? SKIP_LABEL[highlight.skipReason]
            : '판정 데이터를 기다리고 있어요'}
        </p>
      )}

      {/* 판정 재료 — 시연 때 "왜 안 오르나"를 보는 값. 급증은 유니크 채터 수로만 잡힌다 */}
      {highlight.uniqueChatters !== null && (
        <p className="mt-2 text-micro text-muted-foreground/70">
          윈도우 내 채터 <span className="font-mono">{highlight.uniqueChatters}</span>명 · 채팅{' '}
          <span className="font-mono">{highlight.windowChats}</span>건
        </p>
      )}

      <div className="mt-3 flex items-baseline justify-between border-t pt-3">
        <span className="text-caption text-muted-foreground">확정된 구간</span>
        <span className="font-mono text-caption font-semibold">
          {highlight.detectedCount}개
          {highlight.lastDetectedAt && (
            <span className="font-normal text-muted-foreground">
              {' '}
              · 마지막 {formatTime(highlight.lastDetectedAt)}
            </span>
          )}
        </span>
      </div>
    </div>
  )
}

/*
 * 3초 스냅샷 폴링이 도는 화면이라 memo로 감싼다. 폴링 응답은 매번 새로 파싱된 객체라
 * 값이 그대로여도 참조가 달라지는데, useLiveSnapshot이 바뀌지 않은 조각의 참조를
 * 유지해 주므로(hooks/useLiveSnapshot.ts) 여기서 memo가 실제로 걸린다.
 * 측정: perf/console-render.bench.tsx
 */
export default memo(HighlightPanel)
