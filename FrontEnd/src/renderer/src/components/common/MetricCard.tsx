import { type LucideIcon } from 'lucide-react'
import { Card, CardContent } from '@renderer/components/ui/card'
import { Skeleton } from '@renderer/components/ui/skeleton'
import DeltaText from '@renderer/components/common/DeltaText'
import { cn } from '@renderer/lib/utils'

/**
 * KPI 카드 — SC-06 세션 리포트와 SC-09 성장 리포트가 공유한다.
 *
 * `delta`와 `onSelect`는 선택 기능이다.
 *  - SC-06은 응답에 비교값이 없어 delta 없이 정적 카드로 쓴다.
 *  - SC-09는 증감률을 얹고, 카드 자체를 차트 지표 선택기로 쓴다.
 */

interface MetricCardProps {
  label: string
  /** 이미 포맷된 값. 숫자는 mono로 렌더된다 (design.md §1) */
  value: string
  icon: LucideIcon
  caption?: string
  /** 직전 구간 대비 증감률(0.18 = +18%). 없으면 표시하지 않는다. */
  delta?: number
  /** 선택 가능한 카드로 만들 때만 넘긴다. */
  onSelect?: () => void
  selected?: boolean
  className?: string
}

export default function MetricCard({
  label,
  value,
  icon: Icon,
  caption,
  delta,
  onSelect,
  selected = false,
  className
}: MetricCardProps): React.JSX.Element {
  const selectable = onSelect !== undefined

  return (
    <Card
      className={cn(
        'gap-0 py-5',
        selectable && 'cursor-pointer transition-colors hover:border-primary/40',
        selectable &&
          'focus-visible:ring-ring/50 focus-visible:ring-[3px] focus-visible:outline-none',
        selected && 'border-primary ring-primary/20 ring-[3px]',
        className
      )}
      {...(selectable
        ? {
            role: 'button',
            tabIndex: 0,
            'aria-pressed': selected,
            onClick: onSelect,
            onKeyDown: (e: React.KeyboardEvent) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault()
                onSelect()
              }
            }
          }
        : {})}
    >
      <CardContent className="flex flex-col gap-3 px-5">
        <div className="flex items-center gap-2 text-muted-foreground">
          <Icon className="size-4.5" strokeWidth={1.8} aria-hidden />
          <span className="text-xs font-medium">{label}</span>
        </div>

        <div className="flex items-baseline gap-2">
          <p className="font-mono text-metric leading-none font-bold tracking-[-0.03em]">{value}</p>
          {delta !== undefined && <DeltaText delta={delta} />}
        </div>

        {caption && <p className="text-caption text-muted-foreground">{caption}</p>}
      </CardContent>
    </Card>
  )
}

/** 로딩 중 KPI 자리를 지키는 스켈레톤. */
export function MetricCardSkeleton(): React.JSX.Element {
  return (
    <Card className="gap-0 py-5">
      <CardContent className="flex flex-col gap-2.5 px-5">
        <Skeleton className="h-4 w-24" />
        <Skeleton className="h-7 w-20" />
        <Skeleton className="h-3 w-16" />
      </CardContent>
    </Card>
  )
}
