/**
 * 일정 캘린더(SC-04) 타입 — `CALENDAR_EVENTS`, `SCHEDULE_ANNOUNCEMENTS` (API §8).
 */

export type CalendarEventType = 'BROADCAST' | 'DAY_OFF'
export type CalendarEventStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED'

export interface CalendarEvent {
  calendarEventId: number
  platformAccountId: number | null
  eventType: CalendarEventType
  title: string
  memo: string | null
  startAt: string
  endAt: string
  status: CalendarEventStatus
  /** 전환의 출처가 된 약속. null이면 사용자가 직접 만든 일정이다. */
  promiseId: number | null
}

export type PublishStatus = 'DRAFT' | 'PUBLISHED'

export interface ScheduleAnnouncement {
  announcementId: number
  calendarEventId: number
  content: string
  aiGenerated: boolean
  publishStatus: PublishStatus
  publishedAt: string | null
}

/** GET /calendar-events 응답 본문 (API 8.1). */
export interface CalendarEventListResponse {
  items: CalendarEvent[]
  promises: CalendarPromiseItem[]
}

/**
 * 캘린더에 함께 표시할 약속. `PROMISES`가 단일 원천이고 이건 조회 시점에 얹는 읽기 뷰라
 * 시각 없이 날짜만 있다. 날짜를 옮기려면 `PATCH /promises/{id}`를 쓴다(8.3 일정 API가 아님).
 * 화면에는 읽기 전용으로만 얹는다 — 확정/무시/PATCH는 `api/promise.ts`가 생긴 뒤의 몫이다.
 */
export interface CalendarPromiseItem {
  promiseId: number
  content: string
  dueDate: string
  /** 전환된(SCHEDULED) 약속은 일정으로 대신 보이므로 이 목록에 오지 않는다. */
  status: 'PENDING' | 'ON_HOLD'
  /** 활성 일정 역참조. 이 목록에서는 항상 null이지만 계약을 맞춰 둔다. */
  calendarEventId: number | null
}

/** POST /calendar-events 입력 (API 8.2). status는 서버가 항상 SCHEDULED로 시작한다. */
export interface CreateCalendarEventInput {
  platformAccountId: number | null
  eventType: CalendarEventType
  title: string
  memo: string | null
  startAt: string
  endAt: string
}

/**
 * PATCH /calendar-events/{id} 입력 (API 8.3, BE `PatchValue` 계약).
 * 필드를 아예 넣지 않으면 "변경 없음", `null`을 넣으면 "값을 비움"이다.
 * 전 필드 optional인 이유가 그것 — 안 바뀐 필드는 키 자체를 만들지 않아야 한다.
 */
export interface UpdateCalendarEventInput {
  platformAccountId?: number | null
  eventType?: CalendarEventType
  title?: string
  memo?: string | null
  startAt?: string
  endAt?: string
  status?: CalendarEventStatus
}
