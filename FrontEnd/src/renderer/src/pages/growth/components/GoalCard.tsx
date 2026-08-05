import { CheckCircle2, Lightbulb } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@renderer/components/ui/card'
import { Button } from '@renderer/components/ui/button'
import { Progress } from '@renderer/components/ui/progress'
import StatusBadge, { type StatusTone } from '@renderer/components/common/StatusBadge'
import { formatCount, formatPercent } from '@renderer/lib/format'
import { cn } from '@renderer/lib/utils'
import { METRIC_LABEL, METRIC_UNIT } from './metricLabels'
import type { GoalStatus, GrowthGoal } from '@renderer/types/growth'

/**
 * 채택된 성장 목표 카드 — 목표 지표, 달성률, AI 다음 액션을 함께 보여준다.
 *
 * 여기 오는 목표는 로드맵 추천을 채택해 만들어진 것이다(`POST /growth-roadmap/suggestions/{id}/accept`).
 * 목표 자체를 이 카드에서 고치지는 않는다 — 수정·중단은 후속 과제다.
 */

const STATUS_STYLE: Record<GoalStatus, { label: string; tone: StatusTone }> = {
  ACTIVE: { label: '진행 중', tone: 'neutral' },
  ACHIEVED: { label: '달성', tone: 'success' },
  CANCELLED: { label: '중단', tone: 'neutral' }
}

interface GoalCardProps {
  goal: GrowthGoal
  /** 달성 처리 요청 중이면 버튼을 잠근다. */
  pending: boolean
  onAchieve: (growthGoalId: number) => void
}

export default function GoalCard({ goal, pending, onAchieve }: GoalCardProps): React.JSX.Element {
  const status = STATUS_STYLE[goal.status]
  const unit = METRIC_UNIT[goal.metricType]
  // 초과 달성이면 progressRate가 1을 넘는데, 바는 100%에서 멈춘다.
  const barValue = Math.min(100, Math.round(goal.progressRate * 100))
  const achieved = goal.progressRate >= 1
  /**
   * 목표치를 넘긴 진행 중 목표에만 확정 버튼을 준다.
   *
   * 서버가 자동으로 ACHIEVED로 바꾸지 않는 이유는 지표가 다시 내려갈 수 있기 때문이다 —
   * 평균 시청자는 방송마다 오르내리고, 기간이 남았는데 닫으면 이후 방송이 집계에서 빠진다.
   * 그래서 "달성했다"는 판단은 사람이 내린다.
   */
  const canAchieve = achieved && goal.status === 'ACTIVE'

  return (
    <Card>
      <CardHeader>
        <div className="flex items-start justify-between gap-2">
          <CardTitle className="text-sm font-semibold">{METRIC_LABEL[goal.metricType]}</CardTitle>
          <StatusBadge tone={status.tone} label={status.label} className="shrink-0" />
        </div>
        <p className="font-mono text-caption text-muted-foreground">
          {goal.periodStart} ~ {goal.periodEnd}
        </p>
      </CardHeader>

      <CardContent className="flex flex-col gap-4">
        <div className="flex flex-col gap-2">
          <div className="flex items-baseline justify-between gap-2">
            <span className="font-mono text-metric-sm leading-none font-bold tracking-[-0.02em]">
              {formatCount(goal.currentValue)}
              <span className="text-xs font-medium text-muted-foreground">
                {' '}
                / {formatCount(goal.targetValue)}
                {unit}
              </span>
            </span>
            <span
              className={cn(
                'font-mono text-xs font-semibold',
                achieved ? 'text-success' : 'text-muted-foreground'
              )}
            >
              {formatPercent(goal.progressRate)}
            </span>
          </div>
          <Progress
            value={barValue}
            aria-label={`${METRIC_LABEL[goal.metricType]} 달성률`}
            className={cn(achieved && '*:data-[slot=progress-indicator]:bg-success')}
          />
        </div>

        <div className="flex flex-col gap-2">
          <p className="flex items-center gap-1.5 text-caption font-medium text-muted-foreground">
            <Lightbulb className="size-3.5" strokeWidth={1.8} aria-hidden />
            AI 다음 액션
          </p>
          {goal.actions.length === 0 ? (
            <p className="text-xs text-muted-foreground">아직 제안이 없어요.</p>
          ) : (
            <ul className="flex flex-col gap-2.5">
              {goal.actions.map((action) => (
                <li key={action.growthActionId} className="flex flex-col gap-0.5">
                  <p className="text-body font-semibold">{action.title}</p>
                  <p className="text-xs leading-relaxed text-muted-foreground">{action.content}</p>
                </li>
              ))}
            </ul>
          )}
        </div>

        {canAchieve && (
          <Button
            size="sm"
            variant="outline"
            disabled={pending}
            onClick={() => onAchieve(goal.growthGoalId)}
          >
            <CheckCircle2 className="size-3.5" strokeWidth={1.8} aria-hidden />
            {pending ? '처리 중…' : '달성으로 확정'}
          </Button>
        )}
      </CardContent>
    </Card>
  )
}
