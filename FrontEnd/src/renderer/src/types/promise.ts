/**
 * 약속(Promise) API 타입 — `/api/v1/promises` (명세 10.8).
 * AI가 감지한 약속을 스트리머가 확정·무시·관리하는 API의 응답/입력 계약이다.
 */

/**
 * BE `PromiseStatus`와 이름을 반드시 일치시킨다.
 * `SCHEDULED`는 일정이 잡힌 미이행 약속으로, `POST /promises/{id}/schedule`만 만들 수 있다
 * (PATCH로는 도달 불가 — 일정 없이 그 상태가 되면 어느 화면에도 안 보이는 미아가 된다).
 */
export type PromiseStatus =
  'DETECTED' | 'PENDING' | 'ON_HOLD' | 'SCHEDULED' | 'COMPLETED' | 'DISMISSED'

export interface PromiseItem {
  promiseId: number
  sessionId: number
  transcriptId: number
  content: string
  status: PromiseStatus
  dueDate: string | null
  detectedAt: string
  /** 전환으로 만들어진 활성 일정. 없으면 null (취소된 일정은 세지 않는다). */
  calendarEventId: number | null
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

/**
 * POST /promises/{id}/schedule 입력 — 약속을 캘린더 방송 일정으로 전환한다.
 *
 * `eventType`·`platformAccountId`는 보내지 않는다. 전환은 항상 방송 일정이고 계정은 서버가 고른다.
 * `title`을 생략하면 서버가 약속 content를 제목으로 쓴다. 200자를 넘겨 보내면 400이다 —
 * 서버가 조용히 자르지 않기로 했으므로 보내기 전에 길이를 맞춰야 한다.
 */
export interface SchedulePromiseInput {
  startAt: string
  endAt: string
  title?: string
  memo?: string
}
