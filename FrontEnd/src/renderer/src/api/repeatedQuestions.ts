import { apiFetch } from './client'

interface DismissResult {
  questionId: number
  dismissedAt: string
}

/**
 * 반복 질문 답변 완료 처리 — `POST /broadcast-sessions/{id}/repeated-questions/{qid}/dismiss`.
 *
 * 콘솔에서 사라지고, 이후 같은 질문이 임계 이상 다시 늘면 재질문으로 다시 노출된다.
 * 멱등이라 중복 클릭에 안전하다.
 */
export async function dismissRepeatedQuestion(
  sessionId: number,
  questionId: number
): Promise<DismissResult> {
  return apiFetch<DismissResult>(
    `/broadcast-sessions/${sessionId}/repeated-questions/${questionId}/dismiss`,
    { method: 'POST' }
  )
}
