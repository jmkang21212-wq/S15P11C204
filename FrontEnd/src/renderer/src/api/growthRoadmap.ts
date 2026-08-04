import { apiFetch } from './client'
import type { GrowthGoal } from '@renderer/types/growth'
import type { GrowthRoadmap, RoadmapGenerationRequest } from '@renderer/types/growthRoadmap'

/**
 * 목표 로드맵 API — `/api/v1/growth-roadmap` (GrowthRoadmapController).
 *
 * 목표를 만드는 실용적인 경로가 이것뿐이다. `POST /growth-goals`도 있지만 사용자가 지표·목표치·
 * 기간을 직접 정해야 하는데, 무엇을 목표로 삼을지가 애초에 어려운 판단이라 AI 추천을 채택하는
 * 흐름을 쓴다.
 */

const BASE = '/growth-roadmap'

/**
 * 현재 로드맵. 생성된 적이 없어도 404가 아니라 `status: 'NOT_REQUESTED'` + 빈 배열이 온다.
 * 리포트가 완성될 때 서버가 자동으로 생성하므로 조회만 하면 된다.
 */
export async function fetchGrowthRoadmap(): Promise<GrowthRoadmap> {
  return apiFetch<GrowthRoadmap>(BASE)
}

/**
 * 수동 재생성. 202로 즉시 돌아오고 실제 생성은 비동기라, 응답의 status는 큐에 들어간 상태다.
 * 결과는 다시 `fetchGrowthRoadmap()`으로 확인해야 한다.
 */
export async function regenerateGrowthRoadmap(): Promise<RoadmapGenerationRequest> {
  return apiFetch<RoadmapGenerationRequest>(`${BASE}/regenerate`, { method: 'POST' })
}

/**
 * 추천을 실제 목표로 채택한다. 응답은 만들어진 목표이므로 그대로 목표 카드에 쓸 수 있다.
 * 이미 채택한 추천이면 서버가 201 대신 200으로 같은 목표를 돌려준다(멱등).
 */
export async function acceptGoalSuggestion(suggestionId: number): Promise<GrowthGoal> {
  return apiFetch<GrowthGoal>(`${BASE}/suggestions/${suggestionId}/accept`, { method: 'POST' })
}

/** 추천을 무시한다. 204 No Content — apiFetch가 undefined로 돌려준다. */
export async function dismissGoalSuggestion(suggestionId: number): Promise<void> {
  await apiFetch<void>(`${BASE}/suggestions/${suggestionId}/dismiss`, { method: 'POST' })
}
