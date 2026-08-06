import { Bar, BarChart, CartesianGrid, Cell, XAxis, YAxis } from 'recharts'
import { BarChart3 } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@renderer/components/ui/card'
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig
} from '@renderer/components/ui/chart'
import { EmptyState } from '@renderer/components/common/StateView'
import { formatCount } from '@renderer/lib/format'
import type { GrowthSeriesPoint, PeriodType, SeriesMetric } from '@renderer/types/growth'

/**
 * 성장 지표 추이 차트 — SC-09의 주 시각화.
 *
 * 세 지표는 자릿수가 달라 한 축에 겹칠 수 없으므로, 상단 KPI 카드에서 고른
 * 지표 하나만 그린다. 주·월 단위 집계라 연속 시계열이 아닌 이산 구간이므로 막대로 표현한다.
 */

const METRIC_LABEL: Record<SeriesMetric, string> = {
  avgViewers: '평균 시청자',
  totalChat: '총 채팅',
  broadcastMinutes: '방송 시간'
}

/** 값 뒤에 붙는 단위. 축과 툴팁에서 함께 쓴다. */
const METRIC_UNIT: Record<SeriesMetric, string> = {
  avgViewers: '명',
  totalChat: '개',
  broadcastMinutes: '분'
}

interface GrowthTrendChartProps {
  series: GrowthSeriesPoint[]
  metric: SeriesMetric
  periodType: PeriodType
  /**
   * 아직 끝나지 않은 구간의 `periodStart`. 그 막대는 계속 자랄 값이라 완결 구간과
   * 같은 밀도로 그리면 "지표가 꺾였다"로 잘못 읽힌다. 없으면 전부 완결 구간이다.
   */
  inProgressPeriodStart?: string | null
  /**
   * 조회 구간 개수. 헤더의 "최근 N주" 라벨에만 쓴다 — 구간을 정하는 쪽(페이지)이
   * 그대로 넘겨야 상수를 바꿨을 때 라벨이 조용히 어긋나지 않는다.
   */
  bucketCount: number
}

/** 진행 중 막대는 같은 색을 옅게 써서 "아직 확정 아님"을 알린다. */
const IN_PROGRESS_OPACITY = 0.4

/** 주간은 `07.14`, 월간은 `2026.07`로 축 라벨을 만든다. */
function formatPeriodLabel(periodStart: string, periodType: PeriodType): string {
  const [year, month, day] = periodStart.split('-')
  return periodType === 'WEEKLY' ? `${month}.${day}` : `${year}.${month}`
}

export default function GrowthTrendChart({
  series,
  metric,
  periodType,
  inProgressPeriodStart = null,
  bucketCount
}: GrowthTrendChartProps): React.JSX.Element {
  const chartConfig = {
    [metric]: { label: METRIC_LABEL[metric], color: 'var(--chart-1)' }
  } satisfies ChartConfig

  const data = series.map((point) => ({
    label: formatPeriodLabel(point.periodStart, periodType),
    periodStart: point.periodStart,
    periodEnd: point.periodEnd,
    value: point[metric],
    inProgress: point.periodStart === inProgressPeriodStart
  }))
  const hasInProgress = data.some((point) => point.inProgress)

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm font-semibold">
          {METRIC_LABEL[metric]} 추이
          <span className="ml-2 text-xs font-normal text-muted-foreground">
            {`최근 ${bucketCount}${periodType === 'WEEKLY' ? '주' : '개월'}`}
            {hasInProgress && ` · 마지막 ${periodType === 'WEEKLY' ? '주' : '달'}는 진행 중`}
          </span>
        </CardTitle>
      </CardHeader>

      <CardContent>
        {data.length === 0 ? (
          <EmptyState
            icon={BarChart3}
            title="집계된 지표가 없어요"
            description="방송을 마치면 구간별 지표가 쌓입니다."
          />
        ) : (
          <ChartContainer config={chartConfig} className="h-70 w-full">
            <BarChart data={data} margin={{ left: 4, right: 12, top: 8, bottom: 0 }}>
              <CartesianGrid vertical={false} strokeDasharray="3 3" />

              <XAxis
                dataKey="label"
                tickLine={false}
                axisLine={false}
                tickMargin={8}
                className="font-mono text-caption"
              />
              <YAxis
                width={48}
                tickFormatter={(v: number) => formatCount(v)}
                tickLine={false}
                axisLine={false}
                tickMargin={4}
                className="font-mono text-caption"
              />

              <ChartTooltip
                content={
                  <ChartTooltipContent
                    indicator="line"
                    labelFormatter={(_value, payload) => {
                      const item = payload?.[0]?.payload as
                        { periodStart: string; periodEnd: string; inProgress: boolean } | undefined
                      if (!item) return null
                      return (
                        <span className="font-mono">
                          {item.periodStart} ~ {item.periodEnd}
                          {item.inProgress && <span className="ml-1 font-sans">(진행 중)</span>}
                        </span>
                      )
                    }}
                    formatter={(value) => (
                      <span className="font-mono">
                        {formatCount(Number(value))}
                        {METRIC_UNIT[metric]}
                      </span>
                    )}
                  />
                }
              />

              <Bar dataKey="value" fill="var(--chart-1)" radius={[4, 4, 0, 0]}>
                {data.map((point) => (
                  <Cell
                    key={point.periodStart}
                    fillOpacity={point.inProgress ? IN_PROGRESS_OPACITY : 1}
                  />
                ))}
              </Bar>
            </BarChart>
          </ChartContainer>
        )}
      </CardContent>
    </Card>
  )
}
