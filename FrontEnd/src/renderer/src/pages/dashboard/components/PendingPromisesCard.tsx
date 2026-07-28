import { Card } from '@renderer/components/ui/card'
import { Button } from '@renderer/components/ui/button'
import EmptyState from '@renderer/components/common/EmptyState'
import type { PendingPromise } from '@renderer/types/dashboard'

interface PendingPromisesCardProps {
  promises: PendingPromise[]
  count: number
}

export default function PendingPromisesCard({
  promises,
  count,
}: PendingPromisesCardProps): React.JSX.Element {
  const remaining = count - promises.length

  return (
    <Card className="gap-3 p-5">
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold text-muted-foreground">
          미이행 약속 <span className="text-destructive">{count}</span>
        </span>
        <span className="text-xs text-muted-foreground">다음 방송 시작 시 브리핑</span>
      </div>

      {count > 0 ? (
        <ul className="flex flex-col gap-2">
          {promises.map((promise) => (
            <li
              key={promise.promiseId}
              className="flex items-center gap-3 rounded-lg border px-3 py-2"
            >
              <div className="flex-1">
                <p className="text-sm">{promise.content}</p>
                <p className="text-xs text-muted-foreground">
                  기한 {promise.dueDate ?? '미정'}
                </p>
              </div>
              {/* TODO: PATCH /promises/{id} 로 상태 변경 (완료/보류) */}
              <div className="flex gap-1.5">
                <Button variant="outline" size="sm">
                  완료
                </Button>
                <Button variant="ghost" size="sm">
                  보류
                </Button>
              </div>
            </li>
          ))}
          {remaining > 0 && (
            // TODO: 약속 전체 목록 화면(GET /promises)으로 이동
            <p className="pt-1 text-center text-xs text-muted-foreground">더 보기 ({remaining}건)</p>
          )}
        </ul>
      ) : (
        <EmptyState message="미이행 약속이 없어요" />
      )}
    </Card>
  )
}
