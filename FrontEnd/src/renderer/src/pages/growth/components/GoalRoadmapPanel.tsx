import { RefreshCw, Target } from 'lucide-react'
import { Button } from '@renderer/components/ui/button'
import { Card, CardContent } from '@renderer/components/ui/card'
import { Skeleton } from '@renderer/components/ui/skeleton'
import { EmptyState, ErrorState } from '@renderer/components/common/StateView'
import GoalCard from './GoalCard'
import GoalSuggestionCard from './GoalSuggestionCard'
import type { GrowthGoal } from '@renderer/types/growth'
import type { GrowthRoadmap, RoadmapStatus } from '@renderer/types/growthRoadmap'

/**
 * 목표 로드맵 패널 — AI 추천과 채택된 목표를 함께 그린다.
 *
 * 두 조회가 짝을 이룬다. 추천은 `GET /growth-roadmap`, 채택된 목표는 `GET /growth-goals`다.
 * 추천을 채택하면 후자로 넘어가므로 한 패널에 위아래로 두어 흐름이 보이게 한다.
 *
 * 로딩·에러를 두 조회에서 따로 받는 이유는 한쪽이 실패해도 다른 쪽은 쓸 수 있어야 하기
 * 때문이다 — 추천 생성이 실패해도 이미 채택한 목표의 달성률은 계속 봐야 한다.
 */

interface GoalRoadmapPanelProps {
  roadmap: GrowthRoadmap | null
  roadmapLoading: boolean
  roadmapError: Error | null
  onRetryRoadmap: () => void

  goals: GrowthGoal[] | null
  goalsLoading: boolean
  goalsError: Error | null
  onRetryGoals: () => void

  /** 채택·무시 요청 중인 추천 ID. 중복 클릭을 막는다. */
  pendingSuggestionId: number | null
  onAccept: (suggestionId: number) => void
  onDismiss: (suggestionId: number) => void

  /** 달성 확정 요청 중인 목표 ID. */
  pendingGoalId: number | null
  onAchieveGoal: (growthGoalId: number) => void

  /** 수동 재생성 (`POST /growth-roadmap/regenerate`). */
  onRegenerate: () => void
  regenerating: boolean
  /** 재생성 실패 메시지. */
  actionError: string | null
}

/**
 * 추천이 아직 없을 때 이유를 알려주는 문구.
 * 생성은 리포트가 완성될 때 서버가 자동으로 건다 — 사용자가 할 일은 방송을 한 번 마치는 것이다.
 */
const STATUS_NOTICE: Record<RoadmapStatus, { title: string; description: string }> = {
  NOT_REQUESTED: {
    title: '아직 추천할 근거가 없어요',
    description: '방송을 한 번 마치고 리포트가 만들어지면 AI가 목표를 추천해 드려요.'
  },
  WAITING_FOR_DATA: {
    title: '데이터를 더 기다리고 있어요',
    description: '지표가 쌓이면 추천이 만들어져요.'
  },
  PENDING: {
    title: '목표를 준비하고 있어요',
    description: '잠시 뒤에 다시 확인해 주세요.'
  },
  PROCESSING: {
    title: '목표를 만들고 있어요',
    description: '잠시 뒤에 다시 확인해 주세요.'
  },
  FAILED: {
    title: '추천을 만들지 못했어요',
    description: '다시 생성을 요청해 보세요.'
  },
  /**
   * 추천이 만들어져 있는 상태. 목록이 비었다면 세 건을 모두 채택·무시한 뒤다.
   * 무시해도 그 자리를 새 추천이 채우지는 않으므로(서버가 3건을 한 세트로 만든다)
   * 다시 받는 방법을 알려줘야 막다른 길이 되지 않는다.
   */
  READY: {
    title: '추천을 모두 확인했어요',
    description: '"다시 추천"을 누르면 최신 지표로 새 목표를 만들어 드려요.'
  }
}

export default function GoalRoadmapPanel({
  roadmap,
  roadmapLoading,
  roadmapError,
  onRetryRoadmap,
  goals,
  goalsLoading,
  goalsError,
  onRetryGoals,
  pendingSuggestionId,
  onAccept,
  onDismiss,
  pendingGoalId,
  onAchieveGoal,
  onRegenerate,
  regenerating,
  actionError
}: GoalRoadmapPanelProps): React.JSX.Element {
  /**
   * 아직 판단하지 않은 추천만 그린다.
   *
   * 결정한 추천은 남길 이유가 없다 — `ACCEPTED`는 바로 아래 목표 카드가 같은 내용을 이미
   * 보여주므로 두 번 나오고, `DISMISSED`·`SUPERSEDED`는 사용자가 치운 것이라 다시 볼 일이
   * 없다. 응답에는 그대로 오지만(서버가 이력을 보존한다) 화면에서는 걸러낸다.
   */
  const suggestions = (roadmap?.suggestions ?? [])
    .filter((suggestion) => suggestion.status === 'SUGGESTED')
    .sort((a, b) => a.displayOrder - b.displayOrder)
  // 아직 응답이 없으면(첫 로딩·에러) 조회를 시작하지 않은 것과 같으므로 NOT_REQUESTED로 본다.
  const notice = STATUS_NOTICE[roadmap?.status ?? 'NOT_REQUESTED']
  const canRegenerate =
    roadmap !== null && roadmap.status !== 'PENDING' && roadmap.status !== 'PROCESSING'

  return (
    <section className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-sm font-semibold">목표 로드맵</h2>
        {canRegenerate && (
          <Button variant="ghost" size="sm" disabled={regenerating} onClick={onRegenerate}>
            <RefreshCw className="size-[14px]" strokeWidth={1.8} aria-hidden />
            {regenerating ? '요청 중…' : '다시 추천'}
          </Button>
        )}
      </div>

      {/* 새 리포트가 쌓였다는 안내. 지금 추천이 쓸 수 없다는 뜻은 아니다. */}
      {roadmap?.stale && roadmap.status === 'READY' && (
        <p className="text-caption text-muted-foreground">
          새 방송 데이터가 쌓였어요. 다시 추천하면 최신 지표로 목표를 만들어 드려요.
        </p>
      )}

      {actionError && <p className="text-xs text-destructive">{actionError}</p>}

      {/*
        ── AI 추천 ──
        추천은 정확히 세 건이라 3열로 나란히 둔다. 좁은 창에서는 1열로 접히게 해
        카드가 눌려 글자가 뭉치지 않게 한다. `min-w-0`은 긴 제목·근거가 열을 밀어내지 않도록.
      */}
      {roadmapLoading && (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
          {Array.from({ length: 3 }, (_, i) => (
            <Skeleton key={i} className="h-[220px] w-full rounded-xl" />
          ))}
        </div>
      )}

      {!roadmapLoading && roadmapError && (
        <ErrorState message="목표 추천을 불러오지 못했어요." onRetry={onRetryRoadmap} />
      )}

      {!roadmapLoading && !roadmapError && suggestions.length === 0 && (
        <Card>
          <CardContent className="px-0">
            <EmptyState icon={Target} title={notice.title} description={notice.description} />
          </CardContent>
        </Card>
      )}

      {!roadmapLoading && !roadmapError && suggestions.length > 0 && (
        <div className="grid grid-cols-1 items-start gap-4 lg:grid-cols-3">
          {suggestions.map((suggestion) => (
            <div key={suggestion.suggestionId} className="min-w-0">
              <GoalSuggestionCard
                suggestion={suggestion}
                pending={pendingSuggestionId === suggestion.suggestionId}
                onAccept={onAccept}
                onDismiss={onDismiss}
              />
            </div>
          ))}
        </div>
      )}

      {/* ── 채택된 목표 ── 개수가 유동적이라 3열로 채우고 넘치면 다음 줄로 흐른다 */}
      {goalsLoading && (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
          <Skeleton className="h-[200px] w-full rounded-xl" />
        </div>
      )}

      {!goalsLoading && goalsError && (
        <ErrorState message="목표를 불러오지 못했어요." onRetry={onRetryGoals} />
      )}

      {!goalsLoading && !goalsError && goals && goals.length > 0 && (
        <div className="grid grid-cols-1 items-start gap-4 lg:grid-cols-3">
          {goals.map((goal) => (
            <div key={goal.growthGoalId} className="min-w-0">
              <GoalCard
                goal={goal}
                pending={pendingGoalId === goal.growthGoalId}
                onAchieve={onAchieveGoal}
              />
            </div>
          ))}
        </div>
      )}
    </section>
  )
}
