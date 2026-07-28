import { Link } from 'react-router-dom'
import { Card } from '@renderer/components/ui/card'
import { EmptyState } from '@renderer/components/common/StateView'
import { PATHS } from '@renderer/routes/paths'
import { formatCount, formatPercent, formatMonthDay } from '@renderer/lib/format'
import { cn } from '@renderer/lib/utils'
import type { WeeklyMetrics } from '@renderer/types/dashboard'

interface WeeklyMetricsCardProps {
  metrics: WeeklyMetrics | null
}

function ChangeBadge({ rate }: { rate: number }): React.JSX.Element {
  const up = rate >= 0
  return (
    <span className={cn('text-xs font-bold', up ? 'text-success' : 'text-destructive')}>
      {up ? '▲' : '▼'} {formatPercent(Math.abs(rate))}
    </span>
  )
}

export default function WeeklyMetricsCard({ metrics }: WeeklyMetricsCardProps): React.JSX.Element {
  return (
    <Card className="gap-3 p-5">
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold text-muted-foreground">주간 지표</span>
        {metrics && (
          <span className="font-mono text-xs text-muted-foreground">
            {formatMonthDay(metrics.periodStart)} – {formatMonthDay(metrics.periodEnd)}
          </span>
        )}
      </div>

      {metrics ? (
        <div className="flex flex-1 flex-col">
          <div className="flex gap-6">
            <div>
              <div className="text-xs text-muted-foreground">평균 시청자</div>
              <div className="flex items-baseline gap-1.5">
                <span className="font-mono text-xl font-extrabold">
                  {formatCount(metrics.avgViewers)}
                </span>
                <ChangeBadge rate={metrics.avgViewersChangeRate} />
              </div>
            </div>
            <div>
              <div className="text-xs text-muted-foreground">채팅 수</div>
              <div className="flex items-baseline gap-1.5">
                <span className="font-mono text-xl font-extrabold">
                  {formatCount(metrics.totalChat)}
                </span>
                <ChangeBadge rate={metrics.totalChatChangeRate} />
              </div>
            </div>
          </div>

          {/* 추세 스파크라인 (표시용 — 실제 시계열은 성장 리포트에서) */}
          <svg
            viewBox="0 0 300 60"
            preserveAspectRatio="none"
            className="mt-auto h-14 w-full"
            aria-hidden
          >
            <polyline
              points="0,46 43,40 86,43 129,28 172,32 215,18 258,22 300,10"
              fill="none"
              stroke="var(--primary)"
              strokeWidth="2.5"
              strokeLinejoin="round"
              strokeLinecap="round"
            />
          </svg>

          <Link
            to={PATHS.growth}
            className="pt-3 text-xs font-medium text-muted-foreground hover:text-foreground"
          >
            성장 리포트 →
          </Link>
        </div>
      ) : (
        <EmptyState title="주간 지표가 아직 없어요" className="rounded-lg bg-muted/40 py-8" />
      )}
    </Card>
  )
}
