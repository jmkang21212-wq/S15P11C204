/**
 * 대시보드 API 응답 타입 — `GET /api/v1/dashboard` 의 data 필드 계약.
 * mock 데이터와 실제 API가 공통으로 따르는 형태.
 */

import type { PromiseStatus } from './promise'

export type CalendarEventStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED'
export type ReportStatus = 'PROCESSING' | 'COMPLETED' | 'PARTIAL' | 'FAILED'

export interface UpcomingBroadcast {
  /** 세션 시작(POST /broadcast-sessions)에 그대로 실려 가는 실제 캘린더 이벤트 ID. 없으면 null. */
  calendarEventId: number | null
  title: string
  startAt: string
  endAt: string
  status: CalendarEventStatus
}

export interface RecentSession {
  sessionId: number
  title: string
  endedAt: string
  reportStatus: ReportStatus
  avgViewers: number
  peakViewers: number
  summary: string
}

export interface PendingPromise {
  promiseId: number
  sessionId: number
  content: string
  status: PromiseStatus
  dueDate: string | null
  detectedAt: string
}

export interface WeeklyMetrics {
  periodStart: string
  periodEnd: string
  avgViewers: number
  avgViewersChangeRate: number
  totalChat: number
  totalChatChangeRate: number
}

export interface RecentHighlight {
  highlightId: number
  durationMs: number
  clipUrl: string
  thumbnailUrl: string
}

export interface DashboardData {
  upcomingBroadcast: UpcomingBroadcast | null
  recentSession: RecentSession | null
  pendingPromiseCount: number
  pendingPromises: PendingPromise[]
  weeklyMetrics: WeeklyMetrics | null
  recentHighlights: RecentHighlight[]
}
