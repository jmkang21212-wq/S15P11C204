import type { ReportStatus } from '@renderer/types/report'

/**
 * 방송 세션 도메인 타입 — `BroadcastSessionController` (`/api/v1/broadcast-sessions`).
 * console(SC-03)·report(SC-06)·clips(SC-07)·dashboard(SC-02)가 함께 쓴다.
 *
 * BE의 Instant는 Jackson 기본 직렬화로 ISO-8601 문자열로 내려온다.
 */

/** BE `BroadcastStatus` enum과 이름을 반드시 일치시킨다. */
export type BroadcastStatus = 'PREPARING' | 'LIVE' | 'ENDED' | 'FAILED'

/** 공통 페이지 응답. BE PageResponse의 필드는 `content`가 아니라 `items`다. */
export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface BroadcastSessionDetail {
  sessionId: number
  platformAccountId: number
  calendarEventId: number | null
  title: string
  category: string | null
  language: string | null
  thumbnailUrl: string | null
  vodUrl: string | null
  status: BroadcastStatus
  startedAt: string
  endedAt: string | null
}

export interface BroadcastSessionListItem {
  sessionId: number
  title: string
  category: string | null
  thumbnailUrl: string | null
  status: BroadcastStatus
  startedAt: string
  endedAt: string | null
  /** reports 테이블과 LEFT JOIN이라 리포트가 아직 생성되지 않았으면 null이다. */
  reportStatus: ReportStatus | null
}

/**
 * 음성 AI WebSocket 접속용 일회용 티켓.
 * 한 번 쓰면 서버에서 소비되므로 재연결할 때마다 새로 발급받아야 한다.
 */
export interface VoiceTicket {
  ticket: string
  expiresInSec: number
}

export interface StartBroadcastSessionInput {
  platformAccountId: number
  calendarEventId?: number | null
  title: string
  category?: string | null
  /** `ko` / `ko-KR` 형식. BE가 정규식으로 검증한다. */
  language?: string | null
}

export interface StartBroadcastSessionResult {
  sessionId: number
  status: BroadcastStatus
  startedAt: string
}

export interface EndBroadcastSessionResult {
  sessionId: number
  status: BroadcastStatus
  reportStatus: ReportStatus | null
}
