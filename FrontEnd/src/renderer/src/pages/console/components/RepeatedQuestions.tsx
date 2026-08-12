import { useState } from 'react'
import { Button } from '@renderer/components/ui/button'
import { dismissRepeatedQuestion } from '@renderer/api/repeatedQuestions'
import { formatCount } from '@renderer/lib/format'
import type { RepeatedQuestion } from '@renderer/types/console'

/**
 * 콘솔 반복 질문 패널.
 *
 * 백엔드는 노출 중인 질문을 askers 내림차순으로 **전량(≤50)** 내려준다 — 상위 N 컷과
 * 타입 필터는 프론트 몫이다(FAQ는 소수라 서버가 5개로 자르면 필터 후 화면이 빈다).
 */

/** 좁은 사이드 패널에 보여줄 개수. 서버가 이미 askers 순으로 주므로 앞에서 자른다. */
const VISIBLE_LIMIT = 5

/** messages가 askers의 이 배수를 넘으면 한 명이 도배한 것으로 본다. */
const FLOOD_RATIO = 2

interface RepeatedQuestionsProps {
  /** dismiss 호출에 필요 — 호출부가 이미 확정한 세션 ID */
  sessionId: number
  questions: RepeatedQuestion[]
}

export default function RepeatedQuestions({
  sessionId,
  questions
}: RepeatedQuestionsProps): React.JSX.Element {
  /** 낙관적으로 숨긴 질문 — 다음 폴링에서 서버 목록이 따라오면 자연히 정리된다. */
  const [dismissed, setDismissed] = useState<number[]>([])
  const [error, setError] = useState<string | null>(null)

  // CONTEXT는 화면 맥락이 있어야 답하는 질문이라 일괄 답변 목록에서 뺀다.
  // null(판정 전)은 곧 분류되므로 방송 중에는 보여주는 편이 유용하다.
  const visible = questions
    .filter((q) => q.type !== 'CONTEXT' && !dismissed.includes(q.questionId))
    .slice(0, VISIBLE_LIMIT)

  // 미니 창의 "답변 완료"는 여기서 받지 않는다 — 이 컴포넌트는 방송 콘솔 화면에서만
  // 살아서, 사용자가 다른 화면에 가 있으면 요청을 받을 사람이 없었다. 지금은 화면과
  // 무관하게 마운트되는 RequireAuth가 받아 API를 부르고, 이 목록은 다음 폴링에 정리된다.
  // 그래서 참조를 안정시킬 이유가 없어져 useCallback도 걷어냈다.
  const handleDismiss = async (questionId: number): Promise<void> => {
    setError(null)
    setDismissed((prev) => [...prev, questionId])
    try {
      await dismissRepeatedQuestion(sessionId, questionId)
    } catch {
      // 실패하면 다시 보여준다 — 답하지 않은 질문이 조용히 사라지면 안 된다.
      setDismissed((prev) => prev.filter((id) => id !== questionId))
      setError('답변 완료 처리에 실패했어요')
    }
  }

  return (
    <div className="border-t pt-4">
      <div className="mb-3 text-xs font-semibold text-muted-foreground">반복 질문 Top 5</div>

      {visible.length === 0 ? (
        <p className="rounded-lg bg-muted/40 px-3 py-4 text-center text-caption text-muted-foreground">
          아직 반복되는 질문이 없어요
        </p>
      ) : (
        <ul className="flex flex-col gap-2.5">
          {visible.map((q) => (
            <li key={q.questionId} className="flex items-start gap-2">
              <div className="flex-1">
                <p className="text-body">{q.text}</p>
                <div className="mt-0.5 flex items-center gap-1.5">
                  <span className="font-mono text-micro text-muted-foreground">
                    {formatCount(q.askers)}명
                  </span>
                  {/* 한 명이 여러 번 물은 경우 — 실제 관심도와 다르다는 신호 */}
                  {q.messages >= q.askers * FLOOD_RATIO && (
                    <span
                      className="text-micro text-muted-foreground"
                      title={`총 ${formatCount(q.messages)}개 메시지`}
                    >
                      · 도배 포함
                    </span>
                  )}
                  {q.reasked && (
                    <span className="rounded bg-warning/15 px-1 text-micro font-semibold text-warning">
                      다시 물어봄
                    </span>
                  )}
                </div>
              </div>
              <Button
                type="button"
                variant="ghost"
                size="xs"
                onClick={() => void handleDismiss(q.questionId)}
              >
                답변 완료
              </Button>
            </li>
          ))}
        </ul>
      )}

      {error && <p className="mt-2 text-micro text-destructive">{error}</p>}
    </div>
  )
}
