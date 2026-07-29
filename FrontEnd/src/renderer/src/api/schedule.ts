import type { CalendarEvent } from '@renderer/types/schedule'

/**
 * 기간별 일정 목록 조회.
 * 현재는 mock. 실제 연동 시:
 *   const res = await fetch(`/api/v1/calendar-events?from=${from}&to=${to}`, ...)
 *   return (await res.json()).data.items as CalendarEvent[]
 */
export async function getCalendarEvents(_from: string, _to: string): Promise<CalendarEvent[]> {
  await new Promise((resolve) => window.setTimeout(resolve, 200))
  return MOCK_EVENTS
}

/**
 * AI 공지 문구 초안(mock) — 실제로는 POST /calendar-events/{id}/announcement-drafts.
 */
export function buildMockAnnouncement(event: CalendarEvent): string {
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

// ─── mock 데이터 (실제 API 연동 시 제거) — 2026년 7월 기준 ───────────────
const MOCK_EVENTS: CalendarEvent[] = [
  ev(101, 'BROADCAST', '발로란트 랭크 정복 Day 8', '2026-07-01T12:00:00Z', '2026-07-01T15:00:00Z'),
  ev(102, 'DAY_OFF', '휴방', '2026-07-03T00:00:00Z', '2026-07-04T00:00:00Z', null),
  ev(103, 'BROADCAST', '롤토체스 랭크', '2026-07-04T11:00:00Z', '2026-07-04T14:00:00Z'),
  ev(104, 'BROADCAST', '발로란트 랭크 정복 Day 9', '2026-07-06T12:00:00Z', '2026-07-06T15:00:00Z'),
  ev(105, 'BROADCAST', '심야 수다방', '2026-07-08T12:00:00Z', '2026-07-08T15:00:00Z'),
  ev(106, 'BROADCAST', '합방 — 제로킬', '2026-07-10T11:00:00Z', '2026-07-10T14:00:00Z'),
  ev(107, 'BROADCAST', '발로란트 랭크 정복 Day 10', '2026-07-13T12:00:00Z', '2026-07-13T15:00:00Z'),
  ev(108, 'BROADCAST', '심야 수다방', '2026-07-15T12:00:00Z', '2026-07-15T15:00:00Z'),
  ev(109, 'BROADCAST', '롤토체스 랭크', '2026-07-17T11:00:00Z', '2026-07-17T14:00:00Z'),
  ev(110, 'BROADCAST', '발로란트 랭크 정복 Day 12', '2026-07-20T12:00:00Z', '2026-07-20T15:00:00Z')
]

function ev(
  id: number,
  type: CalendarEvent['eventType'],
  title: string,
  startAt: string,
  endAt: string,
  platformAccountId: number | null = 15
): CalendarEvent {
  return {
    calendarEventId: id,
    platformAccountId,
    eventType: type,
    title,
    memo: null,
    startAt,
    endAt,
    status: 'SCHEDULED'
  }
}
