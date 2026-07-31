/**
 * 약속(Promise) API 타입 — `/api/v1/promises` (명세 10.8).
 * AI가 감지한 약속을 스트리머가 확정·무시·관리하는 API의 응답/입력 계약이다.
 */

export type PromiseStatus = 'DETECTED' | 'PENDING' | 'ON_HOLD' | 'COMPLETED' | 'DISMISSED'

export interface PromiseItem {
  promiseId: number
  sessionId: number
  transcriptId: number
  content: string
  status: PromiseStatus
  dueDate: string | null
  detectedAt: string
}

/** POST /promises/{id}/confirm 입력 (API 10.8.2). 둘 다 생략 가능 — AI 문구·기한 미정으로 확정된다. */
export interface ConfirmPromiseInput {
  content?: string
  dueDate?: string
}
