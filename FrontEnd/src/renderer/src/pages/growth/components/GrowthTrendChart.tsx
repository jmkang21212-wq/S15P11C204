import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from 'recharts'
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
}

/** 주간은 `07.14`, 월간은 `2026.07`로 축 라벨을 만든다. */
function formatPeriodLabel(periodStart: string, periodType: PeriodType): string {
  const [year, month, day] = periodStart.split('-')
  return periodType === 'WEEKLY' ? `${month}.${day}` : `${year}.${month}`
}

export default function GrowthTrendChart({
  series,
  metric,
  periodType
}: GrowthTrendChartProps): React.JSX.Element {
  const chartConfig = {
    [metric]: { label: METRIC_LABEL[metric], color: 'var(--chart-1)' }
  } satisfies ChartConfig

  const data = series.map((point) => ({
    label: formatPeriodLabel(point.periodStart, periodType),
    periodStart: point.periodStart,
    periodEnd: point.periodEnd,
    value: point[metric]
  }))

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm font-semibold">
          {METRIC_LABEL[metric]} 추이
          <span className="ml-2 text-xs font-normal text-muted-foreground">
            {periodType === 'WEEKLY' ? '최근 12주' : '최근 6개월'}
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
          <ChartContainer config={chartConfig} className="h-[280px] w-full">
            <BarChart data={data} margin={{ left: 4, right: 12, top: 8, bottom: 0 }}>
              <CartesianGrid vertical={false} strokeDasharray="3 3" />

              <XAxis
                dataKey="label"
                tickLine={false}
                axisLine={false}
                tickMargin={8}
                className="font-mono text-[11px]"
              />
              <YAxis
                width={48}
                tickFormatter={(v: number) => formatCount(v)}
                tickLine={false}
                axisLine={false}
                tickMargin={4}
                className="font-mono text-[11px]"
              />

              <ChartTooltip
                content={
                  <ChartTooltipContent
                    indicator="line"
                    labelFormatter={(_value, payload) => {
                      const item = payload?.[0]?.payload as
                        { periodStart: string; periodEnd: string } | undefined
                      if (!item) return null
                      return (
                        <span className="font-mono">
                          {item.periodStart} ~ {item.periodEnd}
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

              <Bar dataKey="value" fill="var(--chart-1)" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ChartContainer>
        )}
      </CardContent>
    </Card>
  )
}
