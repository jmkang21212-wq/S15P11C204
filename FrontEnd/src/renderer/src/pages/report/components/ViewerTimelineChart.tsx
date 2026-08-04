import { Area, AreaChart, CartesianGrid, ReferenceArea, XAxis, YAxis } from 'recharts'
import { LineChart } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@renderer/components/ui/card'
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig
} from '@renderer/components/ui/chart'
import { EmptyState } from '@renderer/components/common/StateView'
import { formatCount, formatTime } from '@renderer/lib/format'
import type { ReportSegment, SegmentType, ViewerTimelinePoint } from '@renderer/types/report'

/**
 * 시청자 추이 차트 — SC-06의 주 시각화.
 *
 * X축은 방송 시작 기준 경과 시간(분)이다. 리포트의 `segments`가 밀리초 오프셋으로 내려오므로
 * 같은 축을 쓰면 변곡점 구간을 ReferenceArea로 그대로 겹칠 수 있다.
 */

const MINUTE_MS = 60_000

const SEGMENT_STYLE: Record<SegmentType, { label: string; color: string }> = {
  CHAT_SURGE: { label: '채팅 급증 구간', color: 'var(--warning)' }
}

const CHART_CONFIG = {
  viewers: { label: '시청자', color: 'var(--chart-1)' }
} satisfies ChartConfig
function formatElapsed(minute: number, totalMinutes: number): string {
  const m = Math.round(minute % 60)
  if (totalMinutes < 60) return `${String(m)}분`
  const h = Math.floor(minute / 60)
  return `${h}:${String(m).padStart(2, '0')}`
}

interface ViewerTimelineChartProps {
  timeline: ViewerTimelinePoint[]
  segments: ReportSegment[]
  startedAt: string
}

export default function ViewerTimelineChart({
  timeline,
  segments,
  startedAt
}: ViewerTimelineChartProps): React.JSX.Element {
  const startMs = new Date(startedAt).getTime()

  const data = timeline.map((point) => ({
    offsetMin: (new Date(point.measuredAt).getTime() - startMs) / MINUTE_MS,
    measuredAt: point.measuredAt,
    viewers: point.viewerCount
  }))

  const lastOffset = data.length > 0 ? data[data.length - 1].offsetMin : 0

  /** 범례에는 실제로 존재하는 구간 종류만 노출한다. */
  const legendTypes = [...new Set(segments.map((s) => s.segmentType))]

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm font-semibold">시청자 추이</CardTitle>
        {legendTypes.length > 0 && (
          <div className="flex flex-wrap items-center gap-x-4 gap-y-1.5">
            {legendTypes.map((type) => (
              <span
                key={type}
                className="flex items-center gap-1.5 text-caption text-muted-foreground"
              >
                <span
                  className="size-2 rounded-full"
                  style={{ backgroundColor: SEGMENT_STYLE[type].color }}
                  aria-hidden
                />
                {SEGMENT_STYLE[type].label}
              </span>
            ))}
          </div>
        )}
      </CardHeader>

      <CardContent>
        {data.length === 0 ? (
          <EmptyState
            icon={LineChart}
            title="시청자 지표가 없어요"
            description="방송 중 수집된 시청자 데이터가 없습니다."
          />
        ) : (
          <ChartContainer config={CHART_CONFIG} className="h-[260px] w-full">
            <AreaChart data={data} margin={{ left: 4, right: 12, top: 8, bottom: 0 }}>
              <defs>
                <linearGradient id="viewerFill" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="var(--color-viewers)" stopOpacity={0.35} />
                  <stop offset="100%" stopColor="var(--color-viewers)" stopOpacity={0.02} />
                </linearGradient>
              </defs>

              <CartesianGrid vertical={false} strokeDasharray="3 3" />

              <XAxis
                dataKey="offsetMin"
                type="number"
                domain={[0, lastOffset]}
                tickCount={7}
                tickFormatter={(v: number) => formatElapsed(v, lastOffset)}
                tickLine={false}
                axisLine={false}
                tickMargin={8}
                className="font-mono text-caption"
              />
              <YAxis
                width={44}
                tickFormatter={(v: number) => formatCount(v)}
                tickLine={false}
                axisLine={false}
                tickMargin={4}
                className="font-mono text-caption"
              />

              {/* 변곡점 구간 오버레이 — 유입/이탈/채팅 피크 */}
              {segments.map((segment) => {
                const style = SEGMENT_STYLE[segment.segmentType]
                return (
                  <ReferenceArea
                    key={segment.reportSegmentId}
                    x1={segment.startMs / MINUTE_MS}
                    x2={segment.endMs / MINUTE_MS}
                    fill={style.color}
                    fillOpacity={0.1}
                    stroke={style.color}
                    strokeOpacity={0.3}
                  />
                )
              })}

              <ChartTooltip
                content={
                  <ChartTooltipContent
                    indicator="line"
                    labelFormatter={(_value, payload) => {
                      const item = payload?.[0]?.payload as
                        { offsetMin: number; measuredAt: string } | undefined
                      if (!item) return null
                      return (
                        <span className="font-mono">
                          {formatElapsed(item.offsetMin, lastOffset)} ·{' '}
                          {formatTime(item.measuredAt)}
                        </span>
                      )
                    }}
                    formatter={(value) => (
                      <span className="font-mono">{formatCount(Number(value))}명</span>
                    )}
                  />
                }
              />

              <Area
                dataKey="viewers"
                type="monotone"
                stroke="var(--color-viewers)"
                strokeWidth={2}
                fill="url(#viewerFill)"
                dot={false}
                activeDot={{ r: 3 }}
              />
            </AreaChart>
          </ChartContainer>
        )}
      </CardContent>
    </Card>
  )
}
