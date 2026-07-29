/**
 * 방송 콘솔(SC-03) 데이터 타입 — `GET /broadcast-sessions/{id}/live-snapshot`
 * 및 WebSocket 브리핑 이벤트 페이로드 기준.
 */

import type { BroadcastStatus } from '@renderer/types/broadcastSession'

/** @deprecated `BroadcastStatus`(types/broadcastSession.ts)를 직접 쓴다. 기존 화면 호환용 별칭. */
export type SessionStatus = BroadcastStatus
export type BriefingType = 'RISK' | 'CHAT_SUMMARY' | 'FAQ' | 'PROMISE'
export type RiskLevel = 'HIGH' | 'MEDIUM' | 'LOW'

export interface LiveSnapshotSession {
  sessionId: number
  status: SessionStatus
  startedAt: string
  elapsedSeconds: number
  currentViewers: number
}

export interface ConsoleServices {
  microphone: 'CONNECTED' | 'DISCONNECTED'
  stt: 'RUNNING' | 'STOPPED'
  ai: 'RUNNING' | 'STOPPED'
}

export interface Briefing {
  briefingId: number
  briefingType: BriefingType
  message: string
  createdAt: string
  // UI 표현용 확장 필드 — 스냅샷엔 없을 수 있고 WebSocket data에서 전달됨
  level?: RiskLevel
  promiseId?: number
}

export interface Sentiment {
  positiveRatio: number
  neutralRatio: number
  negativeRatio: number
}

export interface RepeatedQuestion {
  questionId: number
  question: string
  occurrenceCount: number
}

export interface LiveSnapshot {
  session: LiveSnapshotSession
  services: ConsoleServices
  briefings: Briefing[]
  sentiment: Sentiment
  repeatedQuestions: RepeatedQuestion[]
  /** 실시간 호응도 0~100 (FR-F-05) — OBS 오버레이 노출용. 실제로는 WebSocket으로 갱신. */
  engagement: number
  lastEventId: string
}
