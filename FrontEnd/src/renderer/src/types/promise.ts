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

/**
 * PATCH /promises/{id} 입력 (API 10.8.3 확장, BE `PatchValue` 계약).
 *
 * 필드를 아예 넣지 않으면 "변경 없음", `null`을 넣으면 "값을 비움"이다. 그래서 안 바뀐
 * 필드는 키 자체를 만들지 않아야 한다. 전부 생략하면 BE가 400(`emptyPatch`)을 낸다.
 *
 * `PENDING`·`ON_HOLD`·`COMPLETED`만 수정 대상이다(`PromiseStatus.isPatchTarget`).
 * 확정 전(`DETECTED`)에는 confirm을 먼저 거쳐야 한다.
 */
export interface UpdatePromiseInput {
  status?: PromiseStatus
  content?: string
  dueDate?: string | null
}
