/**
 * 위험 발언 개인 사전 타입 — `StreamerRiskTermController` (`/api/v1/streamers/me/risk-terms`).
 * 출처: `AI/RISK_POLICY_SPEC.md`, `streamer/risk/dto/response/RiskTermsResponse.java`
 *
 * 기본 사전(AI 서버 `app/detect/terms.yaml`, 표현 18개 + 변형 248개)은 파일에 내장되어
 * 조회 API가 없다. 이 화면이 다루는 것은 그 위에 얹는 **개인 사전**뿐이다.
 */

/**
 * 항목의 종류. 감지 파이프라인에서 하는 일이 다르다.
 *  - `BLOCK` — 사전에 표기를 **추가**한다. 사전 히트는 LLM을 건너뛰고 즉시 알림이라
 *    등록하는 순간 그 판정이 최종이 된다.
 *  - `ALLOW` — 사전에서 표기를 **제거**하고 LLM 프롬프트에도 주입한다. 기본 사전 항목도
 *    이걸로 무력화된다 — 사용자가 "삭제"에 해당하는 동작을 할 수 있는 유일한 경로다.
 */
export type RiskTermKind = 'BLOCK' | 'ALLOW'

/** 금칙어가 걸렸을 때 알릴 등급. 허용어에는 쓰지 않는다(감지를 없애는 쪽이라 등급이 없다). */
export type RiskTermSeverity = 'LOW' | 'MEDIUM' | 'HIGH'

/** 생략 시 서버가 적용하는 값. DB에는 NULL로 들어가지만 응답에는 MEDIUM으로 채워져 온다. */
export const DEFAULT_RISK_SEVERITY: RiskTermSeverity = 'MEDIUM'

export const RISK_SEVERITY_LABEL: Record<RiskTermSeverity, string> = {
  HIGH: '높음',
  MEDIUM: '보통',
  LOW: '낮음'
}

/**
 * `display`는 사용자 입력 원문이다. 서버는 정규화한 `term`으로 매칭·중복 판정을 하지만
 * 그 값은 사람이 읽기 나빠서 응답에 담지 않는다 — 화면에는 항상 원문을 보여준다.
 */
export interface RiskBlockTerm {
  id: number
  display: string
  severity: RiskTermSeverity
}

export interface RiskAllowTerm {
  id: number
  display: string
}

/**
 * 등록 개수·길이 상한. 화면이 "N/200"을 하드코딩하지 않도록 서버가 함께 내려준다.
 * 매칭 엔진의 성질에서 나온 제약이라 운영 중 바뀌지 않는다.
 */
export interface RiskTermLimits {
  maxPerKind: number
  maxTermLength: number
}

/** 조회·등록·삭제가 모두 이 형태를 돌려준다 — 변경 후 재조회가 필요 없다. */
export interface RiskTerms {
  block: RiskBlockTerm[]
  allow: RiskAllowTerm[]
  limits: RiskTermLimits
}

export interface CreateRiskTermInput {
  kind: RiskTermKind
  /** 사용자가 입력한 원문. 정규화는 서버가 한다. */
  term: string
  /** BLOCK일 때만 의미가 있다. ALLOW에 실어 보내면 서버가 무시한다. */
  severity?: RiskTermSeverity
}
