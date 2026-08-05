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
  // level·quote·advice는 AI 음성 서버 WS 이벤트에서만 온다(스냅샷 DB에는 message뿐).
  level?: RiskLevel
  /**
   * PROMISE 브리핑이 가리키는 promises.id — 확정/무시(POST /promises/{id}/confirm·dismiss)
   * 호출에 쓴다. 스냅샷(새로고침·재입장 히스토리)은 BE가 채워 보낸다. 실시간(WS) 카드는
   * AI 서버가 아직 promise_id를 안 실어 보내 undefined로 온다 — 그동안은 버튼을 비활성화한다.
   */
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
 * CHAT_SURGE 하이라이트 판정기의 현재 상태 (관측 전용 — 판정 자체는 백엔드
 * 1초 틱이 한다). 판정 파라미터가 백테스트 미검증이라, 시연·튜닝 때 "왜 안
 * 잡히나"를 화면에서 바로 볼 수 있게 내려온다.
 *
 * 여기의 engagement는 짧은 판정 윈도우(engageWindowSec, 기본 60초) 기준이라
 * 여론(Sentiment)의 180초 engagement와 다른 값이다 — 하이라이트는 시작점이
 * 밀리지 않게 짧은 윈도우로 민감하게 본다.
 */
export interface HighlightStatus {
  /** IDLE(대기) | ACTIVE(감지 중) | COOLDOWN(확정 직후 휴지). 판정기 미등록(방송 전·서버 재시작 직후)이면 null */
  phase: 'IDLE' | 'ACTIVE' | 'COOLDOWN' | null
  /** 평소(최근 15분) 대비 유니크 채터 급증 정도. 가드에 걸린 틱이면 null */
  z: number | null
  /** 진입 임계 — z가 이 값 이상으로 연속되면 하이라이트 진입 */
  zEnter: number | null
  /** 판정 윈도우 유니크 채터 ÷ 시청자 */
  engagement: number | null
  /** 판정 윈도우 안 채팅 수 */
  windowChats: number | null
  /** 판정 윈도우 안 유니크 채터 수 */
  uniqueChatters: number | null
  /**
   * 마지막 틱이 판정을 쉰 이유. 정상 판정 중이면 null.
   * no-viewers(미수신 — 수집기 장애)와 zero-viewers(수신됐지만 0명 — 실제 송출
   * 중이 아니거나 시청자 없음)는 원인이 정반대라 구분해서 내려온다.
   */
  skipReason: 'gap' | 'no-viewers' | 'zero-viewers' | 'warmup' | 'empty-baseline' | 'quiet' | null
  /** 워밍업이 끝나 판정이 시작되는 시각 */
  warmupUntil: string | null
  /** 판정 윈도우 길이(초). 환경변수 튜닝값이라 "최근 N초" 표기는 이 값을 쓴다. 판정기 미등록이면 null */
  engageWindowSec: number | null
  /** 이 세션에서 지금까지 확정된 하이라이트 구간 수 */
  detectedCount: number
  /** 마지막 확정 구간의 끝 시각. 없으면 null */
  lastDetectedAt: string | null
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
  highlight: HighlightStatus
  repeatedQuestions: RepeatedQuestion[]
  lastEventId: string
}
