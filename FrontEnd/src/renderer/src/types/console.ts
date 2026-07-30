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

/**
 * BE(`LiveSnapshotResponse.Services`)는 자유 문자열을 준다 — `collector`는
 * `CollectorState`(RUNNING/RECONNECTING/STOPPED), 나머지는 각자 다른 값 집합이라
 * 하나의 리터럴 유니온으로 묶지 않는다. 현재 화면에서 렌더링하는 곳은 없다(스키마만 맞춰 둠).
 */
export interface ConsoleServices {
  microphone: string
  stt: string
  ai: string
  collector: string
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

/**
 * FE가 그리는 3분할 여론 뷰. BE는 감정 8종 비율(distribution)만 주므로
 * `api/console.ts`가 긍정/중립/부정으로 묶어 만든다 — BE 응답과 1:1이 아니다.
 */
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
