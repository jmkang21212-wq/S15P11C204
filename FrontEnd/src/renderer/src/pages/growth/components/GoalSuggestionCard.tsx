import { useState } from 'react'
import { ChevronDown, Lightbulb, Sparkles } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@renderer/components/ui/card'
import { Button } from '@renderer/components/ui/button'
import { cn } from '@renderer/lib/utils'
import { formatCount, formatMonthDay } from '@renderer/lib/format'
import { METRIC_LABEL, METRIC_UNIT } from './metricLabels'
import type { GrowthGoalSuggestion } from '@renderer/types/growthRoadmap'

interface GoalSuggestionCardProps {
  suggestion: GrowthGoalSuggestion
  /** 채택·무시 요청 중이면 두 버튼을 함께 잠근다. */
  pending: boolean
  onAccept: (suggestionId: number) => void
  onDismiss: (suggestionId: number) => void
}

/**
 * AI 추천 목표 카드.
 *
 * 채택하면 서버가 `GROWTH_GOALS` 행을 만들고, 그 목표는 아래 `GoalCard`로 넘어간다. 그래서
 * 이 카드는 "아직 내 목표가 아닌 제안"으로 읽혀야 한다 — 점선 테두리로 채택된 목표와 구분한다.
 *
 * `rationale`을 반드시 함께 보여준다. 근거 없이 숫자만 제시하면 사용자가 채택을 판단할 수 없고,
 * 목표치가 자기 상황과 안 맞을 때 왜 그런지도 알 수 없다.
 */
export default function GoalSuggestionCard({
  suggestion,
  pending,
  onAccept,
  onDismiss
}: GoalSuggestionCardProps): React.JSX.Element {
  const unit = METRIC_UNIT[suggestion.metricType]
  const [actionsOpen, setActionsOpen] = useState(false)

  return (
    <Card className="gap-3 border-dashed py-4">
      <CardHeader className="gap-1 px-4">
        {/* 지표와 목표치를 한 줄에 둔다 — 카드 세 장이 세로로 쌓이므로 높이를 아끼는 게 우선이다 */}
        <div className="flex items-baseline justify-between gap-2">
          <CardTitle className="flex items-center gap-1.5 text-sm font-semibold">
            <Sparkles className="size-[14px] text-primary" strokeWidth={1.8} aria-hidden />
            {METRIC_LABEL[suggestion.metricType]}
          </CardTitle>
          <span className="shrink-0">
            <span className="font-mono text-metric-sm font-extrabold">
              {formatCount(suggestion.targetValue)}
            </span>
            <span className="text-xs font-medium text-muted-foreground">{unit}</span>
          </span>
        </div>
        <p className="font-mono text-caption text-muted-foreground">
          {formatMonthDay(suggestion.periodStart)} ~ {formatMonthDay(suggestion.periodEnd)}
        </p>
      </CardHeader>

      <CardContent className="flex flex-col gap-2.5 px-4">
        <p className="text-body font-semibold break-words">{suggestion.title}</p>

        {/* 근거는 3줄까지만. 전문은 title 속성으로 남겨 잘린 내용을 잃지 않는다. */}
        <p
          className="line-clamp-3 border-l-2 pl-2.5 text-xs leading-relaxed text-muted-foreground"
          title={suggestion.rationale}
        >
          {suggestion.rationale}
        </p>

        {/*
          실행 액션은 접어 둔다. 항목마다 제목+설명 두 줄이라 셋이면 카드 높이의 절반을
          차지하는데, 채택을 판단하는 데 먼저 필요한 것은 목표치와 근거다.
          shadcn에 Collapsible이 없어 상태 토글로 처리한다 — 여는 동작이 이것뿐이라 충분하다.
        */}
        {suggestion.actions.length > 0 && (
          <div className="flex flex-col gap-2">
            <button
              type="button"
              onClick={() => setActionsOpen((open) => !open)}
              aria-expanded={actionsOpen}
              className="flex items-center gap-1.5 text-caption font-medium text-muted-foreground hover:text-foreground"
            >
              <Lightbulb className="size-[14px] shrink-0" strokeWidth={1.8} aria-hidden />
              실행 액션 {suggestion.actions.length}개
              <ChevronDown
                className={cn(
                  'size-[14px] shrink-0 transition-transform',
                  actionsOpen && 'rotate-180'
                )}
                strokeWidth={1.8}
                aria-hidden
              />
            </button>
            {actionsOpen && (
              <ul className="flex flex-col gap-2">
                {suggestion.actions.map((action) => (
                  <li key={action.suggestionActionId} className="flex flex-col gap-0.5">
                    <p className="text-body font-semibold">{action.title}</p>
                    <p className="text-xs leading-relaxed text-muted-foreground">
                      {action.content}
                    </p>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}

        <div className="flex gap-2">
          <Button
            size="sm"
            className="flex-1"
            disabled={pending}
            onClick={() => onAccept(suggestion.suggestionId)}
          >
            {pending ? '처리 중…' : '목표로 채택'}
          </Button>
          <Button
            size="sm"
            variant="outline"
            disabled={pending}
            onClick={() => onDismiss(suggestion.suggestionId)}
          >
            무시
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}
