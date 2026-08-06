import { apiFetch } from './client'
import type { CreateRiskTermInput, RiskTerms } from '@renderer/types/riskTerm'

/**
 * 위험 발언 개인 사전 — `/streamers/me/risk-terms` (AI/RISK_POLICY_SPEC.md §5).
 *
 * 세 호출이 모두 **갱신된 전체 목록**을 돌려준다. 그래서 등록·삭제 후 재조회하지 않고
 * 응답으로 상태를 교체한다. 목록이 종류당 200개로 묶여 있어 매번 받아도 부담이 없다.
 *
 * 낙관적 업데이트를 하지 않는 이유: 저장되는 값은 서버가 정규화한 표기이고 그 규칙은
 * AI 서버에 있다. 화면이 미리 그려두면 실제 저장 결과와 어긋날 수 있다.
 *
 * 방송 중에 고쳐도 즉시 반영된다 — 서버가 커밋 후 Redis로 알리고 AI 서버가 살아있는
 * 세션의 사전을 갈아끼운다.
 */

const BASE = '/streamers/me/risk-terms'

export async function fetchRiskTerms(): Promise<RiskTerms> {
  return apiFetch<RiskTerms>(BASE)
}

export async function createRiskTerm(input: CreateRiskTermInput): Promise<RiskTerms> {
  return apiFetch<RiskTerms>(BASE, {
    method: 'POST',
    body: JSON.stringify(input)
  })
}

export async function deleteRiskTerm(id: number): Promise<RiskTerms> {
  return apiFetch<RiskTerms>(`${BASE}/${id}`, { method: 'DELETE' })
}
