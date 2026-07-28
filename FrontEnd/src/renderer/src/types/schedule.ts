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
