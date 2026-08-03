/**
 * SC-06 세션 리포트 도메인 타입.
 *
 * 출처: `weekly/2026_0720-0724/설계자료/API 명세서.md` §11.1
 *       `GET /broadcast-sessions/{sessionId}/report`
 * 제약: `BackEnd/spring/laive/src/main/resources/db/migration/V1__baseline_schema.sql`의 CHECK 절과 일치시킨다.
 *
 * 명세서에 명시된 대로 하이라이트의 점수·탐지 사유는 ERD에 저장 컬럼이 없어 응답에 포함되지 않는다.
 * UI에도 만들지 않는다.
 */

import type { BroadcastSessionListItem } from '@renderer/types/broadcastSession'

export type ReportStatus = 'PROCESSING' | 'COMPLETED' | 'PARTIAL' | 'FAILED'

/**
 * 실제 백엔드(`TriggerType`)는 채팅 급증 감지 하나만 구현돼 있다 — 유입/이탈/하이라이트
 * 구간은 애초에 명세서 초안에만 있던 값이라 응답에 나오지 않는다.
 */
export type SegmentType = 'CHAT_SURGE'

export interface ReportSession {
  sessionId: number
  title: string
  startedAt: string
  endedAt: string
}

export interface ReportMetrics {
  avgViewers: number
  peakViewers: number
  totalChat: number
  /** 0~1 비율. 세 값의 합은 1이다. */
  positiveRatio: number
  neutralRatio: number
  negativeRatio: number
}

export interface ViewerTimelinePoint {
  measuredAt: string
  viewerCount: number
}

export interface ReportSegment {
  reportSegmentId: number
  /** 방송 시작 기준 밀리초 오프셋 */
  startMs: number
  endMs: number
  segmentType: SegmentType
}

export interface ReportInsight {
  insightId: number
  title: string
  content: string
}

export interface ReportHighlight {
  highlightId: number
  /** 이 하이라이트를 만든 채팅 급증 구간. 구간 없이 만들어졌으면 null. */
  triggerId: number | null
  startMs: number
  endMs: number
  /**
   * 클립 생성 전이거나 생성에 실패하면 null (명세서 §1.5 nullable 표).
   * 응답에 둘을 구분할 상태 필드가 없어 UI는 "생성 중"으로만 표시한다.
   * TODO: 백엔드에 clipStatus 같은 상태 필드 추가 협의
   */
  clipUrl: string | null
  thumbnailUrl: string | null
}

export interface SessionReport {
  reportId: number
  status: Exclude<ReportStatus, 'PROCESSING'>
  session: ReportSession
  /** AI 요약 생성 실패(PARTIAL) 시 null */
  summary: string | null
  metrics: ReportMetrics
  viewerTimeline: ViewerTimelinePoint[]
  segments: ReportSegment[]
  insights: ReportInsight[]
  highlights: ReportHighlight[]
  /** 생성 완료 전에는 null */
  generatedAt: string | null
}

/** 분석 진행 중일 때의 축약 응답 (명세서: `PROCESSING`이면 202 Accepted와 진행 상태만 반환) */
export interface SessionReportProcessing {
  reportId: number
  status: 'PROCESSING'
  session: ReportSession
  /** 0~1. 서버가 내려주지 않을 수 있다. */
  progress?: number
}

export type SessionReportResult = SessionReport | SessionReportProcessing

/** 타입 가드 — 분석 진행 중 응답인지 판별한다. */
export function isProcessing(result: SessionReportResult): result is SessionReportProcessing {
  return result.status === 'PROCESSING'
}

/**
 * 리포트 목록용 종료 세션 요약.
 * 출처: API 명세서 §10.1 `GET /broadcast-sessions?page&size&status=ENDED`
 *
 * @deprecated `BroadcastSessionListItem`(types/broadcastSession.ts)을 직접 쓴다.
 * BE 응답에는 avgViewers/peakViewers가 없고 대신 category/thumbnailUrl/status가 있다 —
 * 이 별칭은 기존 화면(SessionCard 등)을 단계적으로 옮기는 동안만 유지한다.
 */
export type EndedSessionSummary = BroadcastSessionListItem
