import { BookmarkCheck, GripVertical, Trash2 } from 'lucide-react'
import { Card, CardContent } from '@renderer/components/ui/card'
import { Skeleton } from '@renderer/components/ui/skeleton'
import { EmptyState, ErrorState } from '@renderer/components/common/StateView'
import { formatDateTime } from '@renderer/lib/format'
import { cn } from '@renderer/lib/utils'
import { setScheduleDragData } from './scheduleDnd'
import type { PromiseItem } from '@renderer/types/promise'

/**
 * 기한 미정 약속 목록 — 월 캘린더의 드래그 소스.
 *
 * 방송 콘솔에서 "약속 추가"를 누르면 기한 없이 확정된다(`confirmPromise`에 dueDate를
 * 넘기지 않음). 그런데 캘린더 조회는 `due_date IS NOT NULL`인 약속만 내려주므로, 기한이
 * 없는 동안 약속은 어느 화면에도 나타나지 않는다. 이 목록이 그 구간을 메우고, 사용자가
 * 날짜 칸으로 끌어다 놓으면 그때 `dueDate`가 채워져 캘린더로 넘어간다.
 */

interface UnscheduledPromisesProps {
  promises: PromiseItem[]
  loading: boolean
  error: Error | null
  /** 조회 상한(100건)에 걸려 잘렸으면 남은 건수. 0이면 전부 보여주고 있다. */
  hiddenCount: number
  /** 목록 뷰에서는 떨어뜨릴 날짜 칸이 없어 드래그가 무의미하다 — 안내로 대체한다. */
  dropTargetAvailable: boolean
  /** 드롭 처리 중인 약속. 낙관적 표시 없이 해당 항목만 잠근다. */
  movingPromiseId: number | null
  onRetry: () => void
  /** 약속 삭제(`PATCH status=DISMISSED`). 목록·캘린더·대시보드에서 함께 빠진다. */
  onDismiss: (promiseId: number) => void
}

export default function UnscheduledPromises({
  promises,
  loading,
  error,
  hiddenCount,
  dropTargetAvailable,
  movingPromiseId,
  onRetry,
  onDismiss
}: UnscheduledPromisesProps): React.JSX.Element {
  return (
    <div className="flex flex-col gap-2 p-5">
      <div className="flex items-baseline justify-between gap-2">
        <h2 className="text-sm font-semibold">기한 미정 약속</h2>
        {!loading && !error && promises.length > 0 && (
          <span className="font-mono text-caption text-muted-foreground">{promises.length}건</span>
        )}
      </div>

      <p className="text-caption text-muted-foreground">
        {dropTargetAvailable
          ? '날짜 칸으로 끌어다 놓으면 시간을 정해 일정으로 만듭니다.'
          : '월 캘린더로 바꾸면 날짜에 끌어다 놓을 수 있어요.'}
      </p>

      {loading && (
        <div className="flex flex-col gap-2">
          <Skeleton className="h-14 w-full rounded-lg" />
          <Skeleton className="h-14 w-full rounded-lg" />
        </div>
      )}

      {!loading && error && <ErrorState message="약속을 불러오지 못했어요." onRetry={onRetry} />}

      {!loading && !error && promises.length === 0 && (
        <Card>
          <CardContent className="px-0">
            <EmptyState
              icon={BookmarkCheck}
              title="기한을 정할 약속이 없어요"
              description="방송 중 감지된 약속을 추가하면 여기에 모입니다."
            />
          </CardContent>
        </Card>
      )}

      {!loading && !error && promises.length > 0 && (
        <ul className="flex flex-col gap-2">
          {promises.map((promise) => {
            const moving = movingPromiseId === promise.promiseId
            return (
              <li key={promise.promiseId}>
                <div
                  draggable={dropTargetAvailable && !moving}
                  onDragStart={(event) =>
                    setScheduleDragData(event.dataTransfer, {
                      kind: 'promise',
                      id: promise.promiseId,
                      source: 'unscheduled'
                    })
                  }
                  aria-label={`${promise.content} — 날짜 칸으로 끌어다 놓아 일정으로 전환`}
                  className={cn(
                    'flex items-start gap-2 rounded-lg border border-dashed border-warning/40 bg-warning/5 px-2.5 py-2',
                    dropTargetAvailable && !moving && 'cursor-grab active:cursor-grabbing',
                    moving && 'opacity-50'
                  )}
                >
                  <GripVertical
                    className="mt-0.5 size-[14px] shrink-0 text-muted-foreground"
                    strokeWidth={1.8}
                    aria-hidden
                  />
                  <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                    <span className="text-body leading-snug break-words">{promise.content}</span>
                    <span className="font-mono text-caption text-muted-foreground">
                      {moving ? '처리 중…' : formatDateTime(promise.detectedAt)}
                    </span>
                  </div>
                  <button
                    type="button"
                    onClick={() => onDismiss(promise.promiseId)}
                    disabled={moving}
                    aria-label={`${promise.content} 약속 삭제`}
                    title="약속 삭제"
                    className="shrink-0 text-muted-foreground hover:text-destructive disabled:opacity-50"
                  >
                    <Trash2 className="size-[14px]" strokeWidth={1.8} aria-hidden />
                  </button>
                </div>
              </li>
            )
          })}
        </ul>
      )}

      {/* 상한에 걸려 잘린 것을 조용히 숨기지 않는다 */}
      {!loading && !error && hiddenCount > 0 && (
        <p className="text-caption text-muted-foreground">
          기한을 정하면 나머지 <span className="font-mono">{hiddenCount}</span>건이 이어서
          표시됩니다.
        </p>
      )}
    </div>
  )
}
