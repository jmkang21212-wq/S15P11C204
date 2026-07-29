import { formatPercent } from '@renderer/lib/format'
import { cn } from '@renderer/lib/utils'

/**
 * 긍정/중립/부정 3분할 스택 바 — 방송 콘솔(SC-03)과 세션 리포트(SC-06)가 공유한다.
 *
 * 범례는 공용화하지 않는다: 콘솔은 좌우 정렬 한 줄, 리포트는 세로 목록으로 레이아웃이
 * 달라 억지로 합치면 prop이 레이아웃 플래그로 오염된다. 바 자체만 공용화한다.
 */

interface SentimentBarProps {
  positiveRatio: number
  neutralRatio: number
  negativeRatio: number
  className?: string
}

export default function SentimentBar({
  positiveRatio,
  neutralRatio,
  negativeRatio,
  className
}: SentimentBarProps): React.JSX.Element {
  const total = positiveRatio + neutralRatio + negativeRatio
  const segments = [
    { key: 'positive', label: '긍정', ratio: positiveRatio, className: 'bg-success' },
    { key: 'neutral', label: '중립', ratio: neutralRatio, className: 'bg-muted-foreground/40' },
    { key: 'negative', label: '부정', ratio: negativeRatio, className: 'bg-destructive' }
  ]

  return (
    <div
      className={cn('flex h-2.5 w-full overflow-hidden rounded-full bg-muted', className)}
      role="img"
      aria-label={segments
        .map((s) => `${s.label} ${formatPercent(total > 0 ? s.ratio / total : 0)}`)
        .join(', ')}
    >
      {total > 0 &&
        segments.map((s) => (
          <span key={s.key} className={s.className} style={{ width: `${(s.ratio / total) * 100}%` }} />
        ))}
    </div>
  )
}
