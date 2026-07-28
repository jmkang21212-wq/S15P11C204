/**
 * 대시보드 API 응답 타입 — `GET /api/v1/dashboard` 의 data 필드 계약.
 * mock 데이터와 실제 API가 공통으로 따르는 형태.
 */

export type CalendarEventStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED'
export type ReportStatus = 'PROCESSING' | 'COMPLETED' | 'PARTIAL' | 'FAILED'
export type PromiseStatus = 'DETECTED' | 'PENDING' | 'ON_HOLD' | 'COMPLETED' | 'DISMISSED'

export interface UpcomingBroadcast {
  calendarEventId: number
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
