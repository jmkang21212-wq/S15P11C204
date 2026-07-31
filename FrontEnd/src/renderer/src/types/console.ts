/**
 * 방송 콘솔(SC-03) 데이터 타입 — `GET /broadcast-sessions/{id}/live-snapshot`(명세 10.6)
 * 및 AI 음성 서버 실시간 이벤트(AI/FRONTEND_API.md) 기준.
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

/** 치지직 채팅 수집기 상태. RECONNECTING 구간의 채팅은 유실된다. */
export type CollectorState = 'RUNNING' | 'RECONNECTING' | 'STOPPED'

export interface ConsoleServices {
  /** 음성 경로는 프론트 ↔ AI 직결(WS)이라 백엔드가 관측하지 못해 항상 DISCONNECTED — 실제 값은 useAudioStream이 안다. */
  microphone: 'CONNECTED' | 'DISCONNECTED'
  /** 위와 같은 이유로 항상 STOPPED. */
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
  // UI 표현용 확장 필드 — AI 음성 서버 WS 이벤트에서만 온다(스냅샷 DB에는 message뿐)
  level?: RiskLevel
  promiseId?: number
  /** 근거가 된 발화 원문 (AI/FRONTEND_API.md alert·promise 이벤트의 text) */
  quote?: string
  /** 판정 근거나 권장 조치 (alert의 reason·hits, promise의 기한·확신도) */
  advice?: string
}

/** 채팅 감정 8종 (백엔드 SentimentLabel과 동일한 ASCII 코드). */
export type SentimentLabel =
  'JOY' | 'EXCITEMENT' | 'SUPPORT' | 'SURPRISE' | 'SADNESS' | 'ANGER' | 'CONFUSION' | 'NEUTRAL'

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

/**
 * 답할 수 있는 질문인지 (AI/CHAT_AI_API.md §3).
 * - `FAQ`: 화면을 안 봐도 답할 수 있다
 * - `CONTEXT`: 화면 맥락이 있어야 답할 수 있다 → 일괄 답변 목록에서 제외한다
 * - `null`: 아직 판정 전
 */
export type QuestionType = 'FAQ' | 'CONTEXT' | null

/** 방송 전체에서 묶인 반복 질문 (여론의 3분 윈도우와 달리 누적 집계다). */
export interface RepeatedQuestion {
  /** 폴링해도 바뀌지 않고 재사용되지 않는 불투명 식별자 (BIGINT) */
  questionId: number
  /** 그 묶음에서 가장 많이 쓰인 표현 */
  text: string
  type: QuestionType
  /** 몇 명이 물었나 (유니크). 정렬 기준이자 화면에 표시할 숫자 */
  askers: number
  /** 총 메시지 수. askers와 크게 벌어지면 한 명의 도배다 */
  messages: number
  /** 답변 완료 후 다시 늘어 재노출된 질문 */
  reasked: boolean
  firstAskedAt: string
  lastAskedAt: string
}

export interface LiveSnapshot {
  session: LiveSnapshotSession
  services: ConsoleServices
  briefings: Briefing[]
  sentiment: Sentiment
  repeatedQuestions: RepeatedQuestion[]
  lastEventId: string
}
