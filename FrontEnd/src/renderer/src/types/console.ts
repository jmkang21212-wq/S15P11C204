/**
 * 방송 콘솔(SC-03) 데이터 타입 — `GET /broadcast-sessions/{id}/live-snapshot`
 * 및 WebSocket 브리핑 이벤트 페이로드 기준.
 */

export type SessionStatus = 'PREPARING' | 'LIVE' | 'ENDED' | 'FAILED'
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
  /** 근거가 된 발화 원문 (API 명세 §8 브리핑 payload의 quote) */
  quote?: string
  /** 판정 근거나 권장 조치 (API 명세 §8 브리핑 payload의 advice) */
  advice?: string
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
