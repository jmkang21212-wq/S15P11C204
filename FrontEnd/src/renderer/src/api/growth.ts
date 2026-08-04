import { apiFetch } from './client'
import { buildMockGrowthReport, MOCK_GROWTH_GOALS } from '@renderer/mocks/growth.mock'
import type {
  GrowthActionGenerationRequest,
  GrowthGoal,
  GrowthReport,
  PeriodType,
  UpdateGrowthGoalInput
} from '@renderer/types/growth'

/**
 * SC-09 성장 리포트 데이터 소스 — `GrowthReportController`·`GrowthGoalController`.
 *
 * `GET /growth-goals`는 status를 생략해 전체 상태(ACTIVE·ACHIEVED·CANCELLED)를 받는다.
 * `GoalCard`가 상태별 배지를 이미 그리므로 ACTIVE만 필터링하면 달성/중단 목표가 안 보인다.
 */

/** 되돌리려면 true. 삭제 시 각 함수 상단 3줄만 제거. */
const USE_MOCK = false

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

  const params = new URLSearchParams({ periodType, from, to })
  const report = await apiFetch<GrowthReport>(`/growth-reports?${params}`)
  // BE의 avgViewers는 Integer(nullable)라 이론상 null이 올 수 있다. 화면은 항상 number를 기대한다.
  return {
    ...report,
    summaryMetrics: { ...report.summaryMetrics, avgViewers: report.summaryMetrics.avgViewers ?? 0 }
  }
}

/** 성장 목표와 AI 다음 액션. */
export async function fetchGrowthGoals(): Promise<GrowthGoal[]> {
  if (USE_MOCK) {
    await delay(MOCK_DELAY_MS)
    return MOCK_GROWTH_GOALS
  }

  const data = await apiFetch<{ items: GrowthGoal[] }>('/growth-goals')
  return data.items
}

/**
 * 목표의 상태·목표치·기간을 부분 수정한다.
 *
 * 달성 처리(`status: 'ACHIEVED'`)가 이 경로를 쓴다. 서버가 달성률 100%를 자동으로 확정하지
 * 않는 이유는 지표가 다시 내려갈 수 있기 때문이다 — 평균 시청자는 방송마다 오르내리고,
 * 기간이 남았는데 목표를 닫으면 이후 방송이 집계에서 빠진다. 사람이 확정한다.
 */
export function updateGrowthGoal(
  growthGoalId: number,
  input: UpdateGrowthGoalInput
): Promise<GrowthGoal> {
  return apiFetch<GrowthGoal>(`/growth-goals/${growthGoalId}`, {
    method: 'PATCH',
    body: JSON.stringify(input)
  })
}

export function regenerateGrowthActions(
  growthGoalId: number
): Promise<GrowthActionGenerationRequest> {
  return apiFetch<GrowthActionGenerationRequest>(
    `/growth-goals/${growthGoalId}/actions/regenerate`,
    { method: 'POST' }
  )
}
