import { buildMockGrowthReport, MOCK_GROWTH_GOALS } from '@renderer/mocks/growth.mock'
import type { GrowthGoal, GrowthReport, PeriodType } from '@renderer/types/growth'

/**
 * SC-09 성장 리포트 데이터 소스.
 *
 * 백엔드 미구현 상태 — 현재는 목 데이터를 반환한다.
 * 실연동 시 이 파일만 고치면 되고, 페이지·컴포넌트는 손대지 않는다.
 *
 * TODO: GET /growth-reports?periodType&from&to 연동 (API 명세서 §11.2)
 * TODO: GET /growth-goals?status=ACTIVE 연동         (API 명세서 §11.3)
 *       두 엔드포인트 모두 `{ success, data }` 엔벨로프다.
 *       목표는 `data.items`, 지표는 `data`를 그대로 반환하면 된다.
 */

/** 백엔드가 붙으면 false로 바꾸고 아래 fetch 분기를 채운다. */
const USE_MOCK = true

/** 목 응답에도 지연을 줘 로딩 스켈레톤이 실제로 보이게 한다. */
const MOCK_DELAY_MS = 400

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/**
 * 기간별 성장 지표.
 * 목 모드에서는 `from`·`to`를 무시하고 `periodType`에 맞는 고정 구간을 돌려준다.
 */
export async function fetchGrowthReport(
  periodType: PeriodType,
  from: string,
  to: string
): Promise<GrowthReport> {
  if (USE_MOCK) {
    await delay(MOCK_DELAY_MS)
    return buildMockGrowthReport(periodType)
  }

  // const res = await fetch(
  //   `${BASE_URL}/growth-reports?periodType=${periodType}&from=${from}&to=${to}`
  // )
  // const body = await res.json()
  // return body.data
  void from
  void to
  throw new Error('NOT_IMPLEMENTED')
}

/** 성장 목표와 AI 다음 액션. */
export async function fetchGrowthGoals(): Promise<GrowthGoal[]> {
  if (USE_MOCK) {
    await delay(MOCK_DELAY_MS)
    return MOCK_GROWTH_GOALS
  }

  // const res = await fetch(`${BASE_URL}/growth-goals?status=ACTIVE`)
  // const body = await res.json()
  // return body.data.items
  throw new Error('NOT_IMPLEMENTED')
}
