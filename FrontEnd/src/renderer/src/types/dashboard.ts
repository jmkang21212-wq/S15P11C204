/**
 * 대시보드 API 응답 타입 — `GET /api/v1/dashboard` 의 data 필드 계약.
 * mock 데이터와 실제 API가 공통으로 따르는 형태.
 *
 * BE가 `COALESCE`로 막아 주는 값(요약·시청자 수)은 여기서도 non-null이다.
 * 클립 URL은 예외 — 생성 전·실패면 null이다(`RecentHighlight` 참고).
 * 조각 자체(`upcomingBroadcast` 등)도 null이 정상 상태다 — `DashboardData` 참고.
 */

import type { PromiseStatus } from './promise'

export type CalendarEventStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED'
export type ReportStatus = 'PROCESSING' | 'COMPLETED' | 'PARTIAL' | 'FAILED'

export interface UpcomingBroadcast {
  /**
   * 세션 시작(POST /broadcast-sessions)에 그대로 실려 가는 실제 캘린더 이벤트 ID.
   * 실제 API는 항상 값을 채운다 — 예정 방송이 없으면 `upcomingBroadcast` 자체가 null이다.
   * null은 목 데이터에서만 나오며, 그 경로는 "일정 없이 즉석 시작"으로 처리된다.
   */
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
  /** 클립 생성 전이거나 실패하면 null (BE `ClipUrlResolver`가 정규화). */
  clipUrl: string | null
  thumbnailUrl: string | null
}

export interface DashboardData {
  upcomingBroadcast: UpcomingBroadcast | null
  recentSession: RecentSession | null
  pendingPromiseCount: number
  pendingPromises: PendingPromise[]
  weeklyMetrics: WeeklyMetrics | null
  recentHighlights: RecentHighlight[]
}
