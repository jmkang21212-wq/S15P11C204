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

/** 치지직 채팅 수집기 상태. RECONNECTING 구간의 채팅은 유실된다. */
export type CollectorState = 'RUNNING' | 'RECONNECTING' | 'STOPPED'

export interface ConsoleServices {
  /** 백엔드는 음성 파이프라인이 없어 항상 DISCONNECTED — 실제 값은 프론트 음성 스트림이 안다. */
  microphone: 'CONNECTED' | 'DISCONNECTED'
  /** 위와 동일하게 백엔드는 항상 STOPPED. */
  stt: 'RUNNING' | 'STOPPED'
  /** 채팅 감정 분류 파이프라인 — 최근 여론 스냅샷 갱신 여부로 판단된다. */
  ai: 'RUNNING' | 'STOPPED'
  collector: CollectorState
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

/** 채팅 감정 8종 (백엔드 SentimentLabel과 동일한 ASCII 코드). */
export type SentimentLabel =
  | 'JOY'
  | 'EXCITEMENT'
  | 'SUPPORT'
  | 'SURPRISE'
  | 'SADNESS'
  | 'ANGER'
  | 'CONFUSION'
  | 'NEUTRAL'

/** chat-ai가 계산한 최근 윈도우(기본 180초)의 여론 지표. */
export interface Sentiment {
  windowSec: number
  /** 윈도우 안에서 분석된 채팅 수 */
  analyzedChat: number
  /** 유니크 채터 ÷ 시청자 (0~1). 시청자를 알 수 없으면 null */
  engagement: number | null
  /** 최다 감정. analyzedChat이 0이면 null */
  dominant: SentimentLabel | null
  /** 감정 코드 → 비율(합 1.0). analyzedChat이 0이면 null */
  distribution: Partial<Record<SentimentLabel, number>> | null
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
  lastEventId: string
}
