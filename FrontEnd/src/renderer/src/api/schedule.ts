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
 * 기간별 일정 목록. `from`/`to`는 ISO-8601 instant, 조회 범위는 최대 62일(BE 강제).
 * 응답의 `promises`(기한 잡힌 약속)는 별도 렌더 작업 전까지 쓰지 않는다 (API 8.1).
 */
export async function getCalendarEvents(from: string, to: string): Promise<CalendarEvent[]> {
  const query = new URLSearchParams({ from, to })
  const data = await apiFetch<CalendarEventListResponse>(`${BASE}?${query}`)
  return data.items
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

/**
 * AI 공지 문구 초안 — 실제 `POST /calendar-events/{id}/announcement-drafts`는
 * 명세서 8.4 기준 BE 미구현("보류")이라 그동안 클라이언트에서 생성해 둔다.
 * BE 구현 시 이 함수를 걷어내고 실제 호출로 교체한다.
 */
export function buildAnnouncementDraft(event: CalendarEvent): string {
  const time = new Date(event.startAt).toLocaleTimeString('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  })
  if (event.eventType === 'DAY_OFF') {
    return `📢 오늘은 휴방입니다. 다음 방송에서 만나요! 🙌`
  }
  return `📢 오늘 밤 ${time}! ${event.title} 갑니다. 놓치지 마세요 🔥`
}
