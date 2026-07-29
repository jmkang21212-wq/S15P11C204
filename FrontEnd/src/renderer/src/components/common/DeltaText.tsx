import { ArrowDownRight, ArrowUpRight } from 'lucide-react'
import { cn } from '@renderer/lib/utils'

/**
 * 증감 표기 — design.md §6 "증가/감소: Success/Danger 텍스트 + 화살표, 수치는 mono".
 *
 * MetricCard(SC-06·SC-09)의 화살표 아이콘 버전과 WeeklyMetricsCard(SC-02)의 `▲▼` 문자
 * 버전이 같은 규칙을 다르게 그리고 있어 통일한다. 문자보다 lucide 아이콘이 나머지 UI와
 * 일관되어 아이콘 버전을 채택했다.
 */

interface DeltaTextProps {
  /** 직전 구간 대비 증감률(0.18 = +18%). */
  delta: number
  /** 소수점 자리. 화면마다 다르게 반올림해 왔던 값을 흡수한다. */
  digits?: number
  className?: string
}

export default function DeltaText({
  delta,
  digits = 1,
  className
}: DeltaTextProps): React.JSX.Element {
  if (delta === 0) {
    return (
      <span className={cn('font-mono text-xs font-semibold text-muted-foreground', className)}>
        0%
      </span>
    )
  }

  const up = delta > 0
  const Arrow = up ? ArrowUpRight : ArrowDownRight
  return (
    <span
      className={cn(
        'flex items-center gap-0.5 font-mono text-xs font-semibold',
        up ? 'text-success' : 'text-destructive',
        className
      )}
    >
      <Arrow className="size-[14px]" strokeWidth={2} aria-hidden />
      {up ? '+' : ''}
      {(delta * 100).toFixed(digits)}%
    </span>
  )
}
