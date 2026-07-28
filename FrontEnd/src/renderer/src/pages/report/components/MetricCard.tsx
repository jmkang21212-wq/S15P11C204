import type { LucideIcon } from 'lucide-react'
import { Card, CardContent } from '@renderer/components/ui/card'
import { Skeleton } from '@renderer/components/ui/skeleton'
import { cn } from '@renderer/lib/utils'

interface MetricCardProps {
  label: string
  /** 이미 포맷된 값. 숫자는 mono로 렌더된다 (design.md §1) */
  value: string
  icon: LucideIcon
  caption?: string
  className?: string
}

/**
 * SC-06 상단 KPI 카드.
 *
 * 증감(delta) 표기는 넣지 않는다 — 리포트 응답에 직전 방송 비교값이 없어
 * 없는 데이터를 만들어내지 않기 위함이다.
 */
export default function MetricCard({
  label,
  value,
  icon: Icon,
  caption,
  className
}: MetricCardProps): React.JSX.Element {
  return (
    <Card className={cn('gap-0 py-5', className)}>
      <CardContent className="flex flex-col gap-2 px-5">
        <div className="flex items-center gap-2 text-muted-foreground">
          <Icon className="size-[18px]" strokeWidth={1.8} aria-hidden />
          <span className="text-xs font-medium">{label}</span>
        </div>
        <p className="font-mono text-[28px] leading-none font-extrabold tracking-[-0.03em]">
          {value}
        </p>
        {caption && <p className="text-[11px] text-muted-foreground">{caption}</p>}
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
