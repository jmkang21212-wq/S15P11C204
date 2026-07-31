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

/**
 * 조회할 구간 개수. 주간을 12로 두면 막대가 너무 촘촘해 추세가 읽히지 않는다 —
 * 더 긴 흐름은 월간 탭이 담당하므로 주간은 최근 6주만 본다.
 */
const WEEKLY_BUCKETS = 6
const MONTHLY_BUCKETS = 6

function dateKey(date: Date): string {
  return date.toISOString().slice(0, 10)
}

/**
 * 조회 구간을 만든다. 진행 중인 주·달을 **마지막 버킷으로 포함**한다 — 빼면 이번 주에
 * 한 방송이 다음 주가 되기 전까지 화면에 전혀 나타나지 않아 고장으로 읽힌다.
 *
 * 경계는 BE `GrowthPeriod.aligned()`가 강제한다: 주간은 월요일~일요일, 월간은 1일~말일이
 * 아니면 400이다. 그래서 `to`를 "오늘"로 두지 못하고 진행 중 구간의 마지막 날로 맞춘다.
 * 아직 오지 않은 날짜가 구간에 들어가지만 그 기간의 세션이 없으므로 집계에는 영향이 없다.
 *
 * `now`를 인자로 받는 이유는 렌더 중에 호출되기 때문이다 — 함수 안에서 현재 시각을 읽으면
 * 렌더가 순수하지 않게 된다(React Compiler 규칙).
 */
function periodRange(periodType: PeriodType, now: Date): { from: string; to: string } {
  const todayUtc = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()))

  if (periodType === 'WEEKLY') {
    const weekStart = new Date(todayUtc)
    // getUTCDay()는 일요일이 0이라, 월요일 시작으로 맞추려면 +6 후 7로 나눈 나머지만큼 뺀다.
    weekStart.setUTCDate(weekStart.getUTCDate() - ((weekStart.getUTCDay() + 6) % 7))

    const from = new Date(weekStart)
    from.setUTCDate(from.getUTCDate() - 7 * (WEEKLY_BUCKETS - 1))
    const to = new Date(weekStart)
    to.setUTCDate(to.getUTCDate() + 6)
    return { from: dateKey(from), to: dateKey(to) }
  }

  const from = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1))
  from.setUTCMonth(from.getUTCMonth() - (MONTHLY_BUCKETS - 1))
  // Date.UTC(y, m + 1, 0)은 m월의 말일이다.
  const to = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + 1, 0))
  return { from: dateKey(from), to: dateKey(to) }
}

export default function GrowthReportPage(): React.JSX.Element {
  const [periodType, setPeriodType] = useState<PeriodType>('WEEKLY')
  const [metric, setMetric] = useState<SeriesMetric>('avgViewers')
  // 화면을 열어둔 동안 구간이 흔들리면 안 되므로 기준 시각을 한 번만 잡는다.
  const [now] = useState(() => new Date())

  const { from, to } = periodRange(periodType, now)
  const reportLoader = useCallback(
    () => fetchGrowthReport(periodType, from, to),
    [periodType, from, to]
  )
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
  const series = report?.series ?? []

  /**
   * BE는 `summaryMetrics`를 "마지막 버킷 vs 그 앞 버킷"으로 계산한다. 이제 마지막 버킷이
   * 진행 중인 구간이라 일수가 모자라므로, 그 증감률은 항상 감소로 기울어 쓸 수 없다.
   * 대신 직전 완결 구간의 실제 값을 캡션에 얹어 비교 근거를 그대로 보여준다.
   */
  const previousComplete = series.length > 1 ? series[series.length - 2] : null
  const periodNoun = periodType === 'WEEKLY' ? '주' : '달'
  const inProgressPeriodStart = series.at(-1)?.periodStart ?? null
  const bucketCount = periodType === 'WEEKLY' ? WEEKLY_BUCKETS : MONTHLY_BUCKETS

  const progressCaption = (previous: number | null, unit: string): string =>
    previous === null
      ? `이번 ${periodNoun} 진행 중`
      : `이번 ${periodNoun} 진행 중 · 지난 ${periodNoun} ${formatCount(previous)}${unit}`

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
          {/* delta를 넘기지 않는다 — 진행 중 구간과 완결 구간의 증감률은 비교 근거가 없다. */}
          <MetricCard
            label="평균 시청자"
            value={formatCount(summary.avgViewers)}
            icon={Users}
            caption={progressCaption(previousComplete?.avgViewers ?? null, '명')}
            selected={metric === 'avgViewers'}
            onSelect={() => setMetric('avgViewers')}
          />
          <MetricCard
            label="총 채팅"
            value={formatCount(summary.totalChat)}
            icon={MessageSquare}
            caption={progressCaption(previousComplete?.totalChat ?? null, '개')}
            selected={metric === 'totalChat'}
            onSelect={() => setMetric('totalChat')}
          />
          <MetricCard
            label="방송 시간"
            value={`${formatCount(summary.broadcastMinutes)}분`}
            icon={Timer}
            caption={progressCaption(previousComplete?.broadcastMinutes ?? null, '분')}
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
            <GrowthTrendChart
              series={report.series}
              metric={metric}
              periodType={periodType}
              inProgressPeriodStart={inProgressPeriodStart}
              bucketCount={bucketCount}
            />
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
