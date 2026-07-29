import { useCallback, useState } from 'react'
import { MessageSquare, Target, Timer, Users } from 'lucide-react'
import { Card, CardContent } from '@renderer/components/ui/card'
import { Skeleton } from '@renderer/components/ui/skeleton'
import { Tabs, TabsList, TabsTrigger } from '@renderer/components/ui/tabs'
import { EmptyState, ErrorState } from '@renderer/components/common/StateView'
import PageHeader from '@renderer/components/common/PageHeader'
import MetricCard, { MetricCardSkeleton } from '@renderer/components/common/MetricCard'
import GrowthTrendChart from './components/GrowthTrendChart'
import GoalCard from './components/GoalCard'
import { fetchGrowthGoals, fetchGrowthReport } from '@renderer/api/growth'
import { useAsyncData } from '@renderer/hooks/useAsyncData'
import { formatCount } from '@renderer/lib/format'
import type { PeriodType, SeriesMetric } from '@renderer/types/growth'

/**
 * SC-09 성장 리포트.
 * 주간·월간 지표 추이와 목표 로드맵을 함께 본다.
 *
 * 지표 세 개는 자릿수가 달라 한 차트에 겹칠 수 없어, 상단 KPI 카드가 곧 차트의 지표 선택기다.
 * 목표는 조회 전용이다 — 명세서에 생성 API가 없고 수정(PATCH)은 후속 과제로 미뤘다.
 */

/** 최근 N주 / N개월 범위를 만든다. 목 모드에서는 무시되지만 실연동 시 그대로 쓰인다. */
function periodRange(periodType: PeriodType): { from: string; to: string } {
  const to = new Date()
  const from = new Date(to)
  if (periodType === 'WEEKLY') {
    from.setUTCDate(from.getUTCDate() - 7 * 12)
  } else {
    from.setUTCMonth(from.getUTCMonth() - 6)
  }
  return { from: from.toISOString().slice(0, 10), to: to.toISOString().slice(0, 10) }
}

export default function GrowthReportPage(): React.JSX.Element {
  const [periodType, setPeriodType] = useState<PeriodType>('WEEKLY')
  const [metric, setMetric] = useState<SeriesMetric>('avgViewers')

  const reportLoader = useCallback(() => {
    const { from, to } = periodRange(periodType)
    return fetchGrowthReport(periodType, from, to)
  }, [periodType])
  const {
    data: report,
    loading: reportLoading,
    error: reportError,
    refetch: refetchReport
  } = useAsyncData(reportLoader)

  // 지표와 목표는 별도 조회다. 한쪽이 실패해도 다른 쪽은 그대로 보여준다.
  const goalsLoader = useCallback(() => fetchGrowthGoals(), [])
  const {
    data: goals,
    loading: goalsLoading,
    error: goalsError,
    refetch: refetchGoals
  } = useAsyncData(goalsLoader)

  const summary = report?.summaryMetrics

  return (
    <div className="mx-auto flex max-w-[1400px] flex-col gap-6 p-8">
      <PageHeader
        title="성장 리포트"
        caption="방송 지표가 어떻게 달라지고 있는지, 목표에 얼마나 가까워졌는지 확인하세요."
        actions={
          <Tabs value={periodType} onValueChange={(v) => setPeriodType(v as PeriodType)}>
            <TabsList>
              <TabsTrigger value="WEEKLY">주간</TabsTrigger>
              <TabsTrigger value="MONTHLY">월간</TabsTrigger>
            </TabsList>
          </Tabs>
        }
      />

      {/* KPI — 카드를 누르면 아래 차트의 지표가 바뀐다 */}
      {reportLoading && (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
          {Array.from({ length: 3 }, (_, i) => (
            <MetricCardSkeleton key={i} />
          ))}
        </div>
      )}

      {!reportLoading && summary && (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
          <MetricCard
            label="평균 시청자"
            value={formatCount(summary.avgViewers)}
            icon={Users}
            delta={summary.avgViewersChangeRate}
            caption="직전 구간 대비"
            selected={metric === 'avgViewers'}
            onSelect={() => setMetric('avgViewers')}
          />
          <MetricCard
            label="총 채팅"
            value={formatCount(summary.totalChat)}
            icon={MessageSquare}
            delta={summary.totalChatChangeRate}
            caption="직전 구간 대비"
            selected={metric === 'totalChat'}
            onSelect={() => setMetric('totalChat')}
          />
          <MetricCard
            label="방송 시간"
            value={`${formatCount(summary.broadcastMinutes)}분`}
            icon={Timer}
            delta={summary.broadcastMinutesChangeRate}
            caption="직전 구간 대비"
            selected={metric === 'broadcastMinutes'}
            onSelect={() => setMetric('broadcastMinutes')}
          />
        </div>
      )}

      {/* 2열: 추이 차트 + 목표 로드맵 (design.md §2) */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_340px]">
        <div className="flex min-w-0 flex-col gap-6">
          {reportLoading && <Skeleton className="h-[360px] w-full rounded-xl" />}

          {!reportLoading && reportError && (
            <ErrorState message="성장 지표를 불러오지 못했습니다." onRetry={refetchReport} />
          )}

          {!reportLoading && !reportError && report && (
            <GrowthTrendChart series={report.series} metric={metric} periodType={periodType} />
          )}
        </div>

        <aside className="flex flex-col gap-4">
          <h2 className="text-sm font-semibold">목표 로드맵</h2>

          {goalsLoading && (
            <>
              <Skeleton className="h-[200px] w-full rounded-xl" />
              <Skeleton className="h-[200px] w-full rounded-xl" />
            </>
          )}

          {!goalsLoading && goalsError && (
            <ErrorState message="목표를 불러오지 못했습니다." onRetry={refetchGoals} />
          )}

          {!goalsLoading && !goalsError && goals?.length === 0 && (
            <Card>
              <CardContent className="px-0">
                {/* 목표 생성 API가 없어 안내만 두고 생성 버튼은 넣지 않는다 */}
                <EmptyState
                  icon={Target}
                  title="아직 설정된 목표가 없어요"
                  description="목표가 등록되면 달성률과 AI 제안이 여기에 표시됩니다."
                />
              </CardContent>
            </Card>
          )}

          {!goalsLoading &&
            !goalsError &&
            goals?.map((goal) => <GoalCard key={goal.growthGoalId} goal={goal} />)}
        </aside>
      </div>
    </div>
  )
}
