import { apiFetch } from './client'
import type {
  CalendarEvent,
  CalendarEventListResponse,
  CreateCalendarEventInput,
  UpdateCalendarEventInput
} from '@renderer/types/schedule'

/**
 * 일정 캘린더 API — `/api/v1/calendar-events` (CalendarEventController).
 * 전 엔드포인트가 Bearer JWT를 요구한다 (api/client.ts가 자동 주입·401 재시도).
 */

const BASE = '/calendar-events'

/**
 * 기간별 일정·약속 목록. `from`/`to`는 ISO-8601 instant, 조회 범위는 최대 62일(BE 강제).
 * `items`는 일정, `promises`는 기한이 잡힌 진행 중 약속이다 (API 8.1).
 */
export async function getCalendarSchedule(
  from: string,
  to: string
): Promise<CalendarEventListResponse> {
  const query = new URLSearchParams({ from, to })
  return apiFetch<CalendarEventListResponse>(`${BASE}?${query}`)
}

/** 201 Created. SCHEDULED 상태로 생성된다. */
export async function createCalendarEvent(input: CreateCalendarEventInput): Promise<CalendarEvent> {
  return apiFetch<CalendarEvent>(BASE, { method: 'POST', body: JSON.stringify(input) })
}

/** 부분 수정. patch에 없는 키는 유지, `null`은 값을 비운다 (PatchValue 계약). */
export async function updateCalendarEvent(
  calendarEventId: number,
  patch: UpdateCalendarEventInput
): Promise<CalendarEvent> {
  return apiFetch<CalendarEvent>(`${BASE}/${calendarEventId}`, {
    method: 'PATCH',
    body: JSON.stringify(patch)
  })
}

/** BE에 DELETE 엔드포인트가 없어 취소는 상태 변경으로 처리한다 (API 8.3). */
export async function cancelCalendarEvent(calendarEventId: number): Promise<CalendarEvent> {
  return updateCalendarEvent(calendarEventId, { status: 'CANCELLED' })
}
