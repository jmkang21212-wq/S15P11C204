/**
 * 음성 스트리밍(AI 서버) 타입 — `AI/FRONTEND_API.md` 규격.
 *
 * 프론트는 오디오를 보내고 `alert`(위험 발언) · `promise`(약속)만 받는다.
 * 전사·감지 결과 저장은 AI 서버가 Spring으로 직접 하므로 프론트가 관여하지 않는다.
 * 서버가 내려주는 필드는 snake_case라 그대로 둔다.
 */

export type AudioStreamStatus = 'IDLE' | 'CONNECTING' | 'STREAMING' | 'ERROR'

export type AlertSource = 'dictionary' | 'llm'
export type AlertSeverity = 'high' | 'medium' | 'low'
export type AlertCategory =
  | 'profanity'
  | 'insult'
  | 'threat'
  | 'harassment'
  | 'discrimination'

/** 사전매칭으로 걸린 단어. `start`/`end`는 `text` 안의 문자 인덱스다. */
export interface AlertHit {
  matched: string
  severity: AlertSeverity
  category: AlertCategory
  start: number
  end: number
}

export interface AlertEvent {
  type: 'alert'
  source: AlertSource
  seq: number
  /** 여러 단어가 걸리면 그중 가장 심각한 값 */
  severity: AlertSeverity
  category: AlertCategory
  /** 해당 발화 전문 */
  text: string
  start_ts: number
  end_ts: number
  /** dictionary일 때만 */
  hits?: AlertHit[]
  /** llm일 때만 — 판정 근거 한 문장 */
  reason?: string
}

export interface PromiseEvent {
  type: 'promise'
  seq: number
  /** 정리된 약속 내용 — 화면에 이걸 보여준다 */
  content: string
  /** 발화 원문 */
  text: string
  deadline_text: string | null
  /** 해석된 절대 시각(epoch 초). 기한 없거나 조건부면 null */
  deadline_at: number | null
  /** 조건부 약속의 조건. 아니면 null */
  condition: string | null
  confidence: number
  spoken_at: number
}

export interface ReadyEvent {
  type: 'ready'
  session_id: string
  sample_rate: number
  chunk: number
}

/** 개발용 — 핸드셰이크에 `transcripts: true`를 보낼 때만 온다. */
export interface TranscriptEvent {
  type: 'transcript'
  seq: number
  text: string
  start_ts: number
  end_ts: number
  forced: boolean
}

export type AudioStreamEvent = ReadyEvent | AlertEvent | PromiseEvent | TranscriptEvent
