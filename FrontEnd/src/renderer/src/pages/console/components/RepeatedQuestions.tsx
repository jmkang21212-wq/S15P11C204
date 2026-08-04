import { useCallback, useEffect, useState } from 'react'
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

  // setState를 모두 updater 형태로 써서 sessionId 외의 의존성이 없다 —
  // 아래 IPC 구독이 매 렌더마다 재구독되지 않게 하려면 참조가 안정적이어야 한다.
  const handleDismiss = useCallback(
    async (questionId: number): Promise<void> => {
      setError(null)
      setDismissed((prev) => [...prev, questionId])
      try {
        await dismissRepeatedQuestion(sessionId, questionId)
      } catch {
        // 실패하면 다시 보여준다 — 답하지 않은 질문이 조용히 사라지면 안 된다.
        setDismissed((prev) => prev.filter((id) => id !== questionId))
        setError('답변 완료 처리에 실패했어요')
      }
    },
    [sessionId]
  )

  // 미니 창은 토큰이 없어 dismiss를 직접 부를 수 없다. 인증을 가진 이 창(콘솔)이
  // main을 거쳐 온 요청을 받아 대신 호출한다.
  useEffect(() => {
    const bridge = window.api.question
    if (!bridge) return
    return bridge.onDismissRequest((payload) => {
      if (payload.sessionId !== sessionId) return
      void handleDismiss(payload.questionId)
    })
  }, [sessionId, handleDismiss])

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
