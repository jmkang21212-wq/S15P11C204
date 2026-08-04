import { Link } from 'react-router-dom'
import { Card } from '@renderer/components/ui/card'
import { EmptyState } from '@renderer/components/common/StateView'
import DeltaText from '@renderer/components/common/DeltaText'
import { PATHS } from '@renderer/routes/paths'
import { formatCount, formatMonthDay } from '@renderer/lib/format'
import type { WeeklyMetrics } from '@renderer/types/dashboard'

interface WeeklyMetricsCardProps {
  metrics: WeeklyMetrics | null
}

/**
 * 지표 한 줄 — 이름은 왼쪽, 값과 증감률은 오른쪽.
 *
 * 나란한 두 열이 아니라 위아래 두 줄로 둔 이유는 같은 그리드 줄의 "미이행 약속" 카드다.
 * 그쪽이 테두리 있는 항목을 최대 3줄 쌓아 카드가 높아지는데, 이쪽 숫자를 한 줄에 몰아
 * 두면 남는 높이가 전부 빈칸이 된다. 같은 행 리듬을 쓰면 두 카드가 한 쌍으로 읽힌다.
 *
 * 알아둘 점: BE `DashboardQueryService.changeRate`는 `previous == 0`일 때도 0을 돌려주므로
 * `0%`는 "변화 없음"과 "비교할 지난주가 없음"을 함께 뜻한다. 카드 캡션이 "직전 주 대비"로
 * 기준을 밝히고 있어 그대로 노출하되, 둘을 정확히 나누려면 BE가 이 경우 null을 내려줘야 한다.
 */
function MetricRow({
  label,
  value,
  delta
}: {
  label: string
  value: string
  delta: number
}): React.JSX.Element {
  return (
    <div className="flex items-center justify-between gap-3 rounded-lg border px-3 py-3">
      <span className="text-sm text-muted-foreground">{label}</span>
      <div className="flex items-baseline gap-2">
        <span className="font-mono text-metric-sm font-extrabold">{value}</span>
        <DeltaText delta={delta} digits={0} />
      </div>
    </div>
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
          {/*
            추세 그래프는 두지 않는다. 대시보드 응답에 시계열이 없어 이전에는 하드코딩된
            좌표를 그리고 있었고, 실제로 성장하지 않아도 우상향으로 보였다.
            구간별 추이는 성장 리포트(SC-09)가 실제 데이터로 담당한다.

            그래프가 빠진 만큼 `justify-center`로 지표 묶음을 세로 가운데에 두어, 남는 높이가
            한쪽에 몰리지 않고 위아래로 나뉘게 한다.
          */}
          <div className="flex flex-1 flex-col justify-center gap-2">
            <MetricRow
              label="평균 시청자"
              value={formatCount(metrics.avgViewers)}
              delta={metrics.avgViewersChangeRate}
            />
            <MetricRow
              label="채팅 수"
              value={formatCount(metrics.totalChat)}
              delta={metrics.totalChatChangeRate}
            />
            {/* 증감률이 무엇과의 비교인지 밝힌다. 배지만 있으면 기준을 알 수 없다. */}
            <p className="text-caption text-muted-foreground">직전 주 대비</p>
          </div>

          <Link
            to={PATHS.growth}
            className="pt-3 text-xs font-medium text-muted-foreground hover:text-foreground"
          >
            성장 리포트에서 추이 보기 →
          </Link>
        </div>
      ) : (
        <EmptyState
          title="주간 지표가 아직 없어요"
          className="flex-1 rounded-lg bg-muted/40 py-8"
        />
      )}
    </Card>
  )
}
