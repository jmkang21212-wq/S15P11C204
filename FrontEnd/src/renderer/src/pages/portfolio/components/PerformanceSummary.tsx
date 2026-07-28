import { CalendarDays, Clock, MessageSquare, Radio, TrendingUp, Users } from 'lucide-react'
import MetricCard from '@renderer/components/common/MetricCard'
import { formatCount } from '@renderer/lib/format'
import type { PortfolioPerformance } from '@renderer/types/portfolio'

/**
 * 방송 실적 요약 — 편집 화면과 공개 뷰가 함께 쓴다.
 *
 * 전부 서버 집계값이라 읽기 전용이다(`PATCH /portfolio/me` 본문에 없음).
 * `primaryTimeRange`만 문자열이고 나머지는 수치다.
 */

interface PerformanceSummaryProps {
  performance: PortfolioPerformance
}

export default function PerformanceSummary({
  performance
}: PerformanceSummaryProps): React.JSX.Element {
  return (
    <div className="grid grid-cols-2 gap-4 lg:grid-cols-3">
      <MetricCard label="평균 시청자" value={formatCount(performance.avgViewers)} icon={Users} />
      <MetricCard
        label="최고 동시 시청자"
        value={formatCount(performance.peakViewers)}
        icon={TrendingUp}
      />
      <MetricCard
        label="주당 방송"
        value={`${formatCount(performance.broadcastsPerWeek)}회`}
        icon={CalendarDays}
      />
      <MetricCard
        label="총 방송 횟수"
        value={formatCount(performance.totalBroadcastCount)}
        icon={Radio}
      />
      <MetricCard
        label="누적 채팅"
        value={formatCount(performance.totalChat)}
        icon={MessageSquare}
      />
      <MetricCard
        label="주 방송 시간대"
        value={performance.primaryTimeRange}
        icon={Clock}
        caption="최근 방송 기준"
      />
    </div>
  )
}
