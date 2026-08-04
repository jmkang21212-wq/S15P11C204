import { useState } from 'react'
import { Card } from '@renderer/components/ui/card'
import { Button } from '@renderer/components/ui/button'
import { EmptyState } from '@renderer/components/common/StateView'
import { ApiError } from '@renderer/api/client'
import { updatePromise } from '@renderer/api/promise'
import type { PendingPromise } from '@renderer/types/dashboard'

interface PendingPromisesCardProps {
  promises: PendingPromise[]
  count: number
  /**
   * 완료 처리 후 대시보드를 다시 읽는다. 목록·건수가 서버 값으로 맞춰지고, 3건 상한에
   * 걸려 있던 다음 약속이 바로 올라온다. 재조회 중 화면이 깜빡이지 않는 것은
   * `DashboardPage`가 마지막 성공 응답을 붙들어 두기 때문이다.
   */
  onChanged: () => void
}

/**
 * 미이행 약속 카드.
 *
 * 여기서 할 수 있는 것은 "지켰다" 체크 하나뿐이다. 보류·기한 변경·삭제는 캘린더 화면이
 * 담당한다 — 대시보드는 방송 전 상태를 훑는 곳이고, 같은 기능을 두 화면에 나눠 두면
 * 어디서 무엇을 하는지가 흐려진다.
 *
 * 보류 버튼을 두지 않은 실질적인 이유도 있다. `ON_HOLD`는 미이행 집계에 그대로 포함되어
 * 눌러도 이 카드에서 사라지지 않는다 — 사용자에게는 아무 일도 안 한 것처럼 보인다.
 */

/**
 * 항목 아래에 붙는 부가 정보.
 * `SCHEDULED`는 전환 시 기한을 비워 두므로(일정이 기한 역할을 대신한다) "기한 미정"으로
 * 표시하면 사실과 어긋난다.
 */
function promiseMeta(promise: PendingPromise): string {
  if (promise.status === 'SCHEDULED') return '일정 잡힘'
  const due = promise.dueDate ?? '미정'
  return promise.status === 'ON_HOLD' ? `보류 · 기한 ${due}` : `기한 ${due}`
}

export default function PendingPromisesCard({
  promises,
  count,
  onChanged
}: PendingPromisesCardProps): React.JSX.Element {
  const [completingId, setCompletingId] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  // 서버가 3건까지만 주므로(PROMISE_LIMIT) 나머지는 건수로만 알린다.
  const remaining = count - promises.length

  const handleComplete = async (promiseId: number): Promise<void> => {
    setCompletingId(promiseId)
    setError(null)
    try {
      await updatePromise(promiseId, { status: 'COMPLETED' })
      onChanged()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '약속을 완료 처리하지 못했어요.')
    } finally {
      setCompletingId(null)
    }
  }

  return (
    <Card className="gap-3 p-5">
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold text-muted-foreground">
          미이행 약속 <span className="text-destructive">{count}</span>
        </span>
        <span className="text-xs text-muted-foreground">다음 방송 시작 시 브리핑</span>
      </div>

      {count > 0 ? (
        // 서버가 3건까지만 내려주지만(PROMISE_LIMIT) 약속 내용이 길면 줄바꿈으로 카드가
        // 세로로 늘어난다. 그러면 같은 줄의 주간 지표 카드에 빈 공간이 생기므로 높이를
        // 묶고 넘치는 만큼만 스크롤한다. 스크롤바는 감춘다 — 캘린더 날짜 칸과 같은 방식.
        <ul className="flex max-h-52 flex-col gap-2 overflow-y-auto [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
          {promises.map((promise) => {
            const completing = completingId === promise.promiseId
            return (
              <li
                key={promise.promiseId}
                className="flex shrink-0 items-center gap-3 rounded-lg border px-3 py-2"
              >
                <div className="min-w-0 flex-1">
                  <p className="text-sm break-words">{promise.content}</p>
                  <p className="text-xs text-muted-foreground">{promiseMeta(promise)}</p>
                </div>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={completingId !== null}
                  onClick={() => void handleComplete(promise.promiseId)}
                >
                  {completing ? '처리 중…' : '완료'}
                </Button>
              </li>
            )
          })}

          {/* 이동할 화면이 없으므로 링크처럼 보이지 않게 사실만 적는다 */}
          {remaining > 0 && (
            <p className="pt-1 text-center text-xs text-muted-foreground">그 외 {remaining}건</p>
          )}

          {error && <p className="pt-1 text-xs text-destructive">{error}</p>}
        </ul>
      ) : (
        // `flex-1`로 카드의 남은 높이를 채운다. 없으면 옆 카드가 더 높을 때 회색 박스 아래에
        // 흰 여백이 남는다. EmptyState가 내용을 이미 가운데 정렬하므로 문구는 그대로 중앙에 온다.
        <EmptyState title="미이행 약속이 없어요" className="flex-1 rounded-lg bg-muted/40 py-8" />
      )}
    </Card>
  )
}
