import { apiFetch } from './client'
import { MOCK_DASHBOARD } from '@renderer/mocks/dashboard.mock'
import type { DashboardData } from '@renderer/types/dashboard'

/**
 * 대시보드 데이터 — `GET /api/v1/dashboard` (DashboardController).
 *
 * 응답의 조각은 각각 독립적으로 null일 수 있다(`DashboardQueryService`가 `orElse(null)`로
 * 내려준다). 예정 방송이 없거나, 끝낸 방송이 없거나, 지난 주 데이터가 없는 상태가 전부
 * 정상이므로 화면은 조각별로 빈 상태를 그린다.
 *
 * 리스트는 서버가 상한을 정한다 — 미이행 약속 3건, 최근 클립 5건.
 */

/** 되돌리려면 true. 삭제 시 함수 상단 3줄만 제거. */
const USE_MOCK = false

/** 목 응답에도 지연을 줘 로딩 스켈레톤이 실제로 보이게 한다. */
const MOCK_DELAY_MS = 300

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export async function getDashboard(): Promise<DashboardData> {
  if (USE_MOCK) {
    await delay(MOCK_DELAY_MS)
    return MOCK_DASHBOARD
  }

  return apiFetch<DashboardData>('/dashboard')
}
