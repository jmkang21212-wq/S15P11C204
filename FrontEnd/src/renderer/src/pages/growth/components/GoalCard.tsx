import { Lightbulb } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@renderer/components/ui/card'
import { Progress } from '@renderer/components/ui/progress'
import StatusBadge, { type StatusTone } from '@renderer/components/common/StatusBadge'
import { formatCount, formatPercent } from '@renderer/lib/format'
import { cn } from '@renderer/lib/utils'
import type { GoalStatus, GrowthGoal, MetricType } from '@renderer/types/growth'

/**
 * 성장 목표 카드 — 목표 지표, 달성률, AI 다음 액션을 함께 보여준다.
 *
 * 목표 생성·수정 API는 이번 범위가 아니라 읽기 전용이다
 * (명세서에 `POST /growth-goals`가 없고 `PATCH`는 후속 과제).
 */

const METRIC_LABEL: Record<MetricType, string> = {
  AVG_VIEWERS: '평균 시청자',
  BROADCASTS_PER_WEEK: '주당 방송 횟수',
  TOTAL_CHAT: '총 채팅',
  BROADCAST_MINUTES: '방송 시간'
}

const METRIC_UNIT: Record<MetricType, string> = {
  AVG_VIEWERS: '명',
  BROADCASTS_PER_WEEK: '회',
  TOTAL_CHAT: '개',
  BROADCAST_MINUTES: '분'
}

const STATUS_STYLE: Record<GoalStatus, { label: string; tone: StatusTone }> = {
  ACTIVE: { label: '진행 중', tone: 'neutral' },
  ACHIEVED: { label: '달성', tone: 'success' },
  CANCELLED: { label: '중단', tone: 'neutral' }
}

interface GoalCardProps {
  goal: GrowthGoal
}

export default function GoalCard({ goal }: GoalCardProps): React.JSX.Element {
  const status = STATUS_STYLE[goal.status]
  const unit = METRIC_UNIT[goal.metricType]
  // 초과 달성이면 progressRate가 1을 넘는데, 바는 100%에서 멈춘다.
  const barValue = Math.min(100, Math.round(goal.progressRate * 100))
  const achieved = goal.progressRate >= 1

  return (
    <Card>
      <CardHeader>
        <div className="flex items-start justify-between gap-2">
          <CardTitle className="text-sm font-semibold">{METRIC_LABEL[goal.metricType]}</CardTitle>
          <StatusBadge tone={status.tone} label={status.label} className="shrink-0" />
        </div>
        <p className="font-mono text-[11px] text-muted-foreground">
          {goal.periodStart} ~ {goal.periodEnd}
        </p>
      </CardHeader>

      <CardContent className="flex flex-col gap-4">
        <div className="flex flex-col gap-2">
          <div className="flex items-baseline justify-between gap-2">
            <span className="font-mono text-[20px] leading-none font-extrabold tracking-[-0.02em]">
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
            className={cn(achieved && '[&>[data-slot=progress-indicator]]:bg-success')}
          />
        </div>

        <div className="flex flex-col gap-2">
          <p className="flex items-center gap-1.5 text-[11px] font-medium text-muted-foreground">
            <Lightbulb className="size-[14px]" strokeWidth={1.8} aria-hidden />
            AI 다음 액션
          </p>
          {goal.actions.length === 0 ? (
            <p className="text-xs text-muted-foreground">아직 제안이 없어요.</p>
          ) : (
            <ul className="flex flex-col gap-2.5">
              {goal.actions.map((action) => (
                <li key={action.growthActionId} className="flex flex-col gap-0.5">
                  <p className="text-[13px] font-semibold">{action.title}</p>
                  <p className="text-xs leading-relaxed text-muted-foreground">{action.content}</p>
                </li>
              ))}
            </ul>
          )}
        </div>
      </CardContent>
    </Card>
  )
}
