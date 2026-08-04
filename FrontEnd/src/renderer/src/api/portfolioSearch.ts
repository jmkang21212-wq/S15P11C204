import { apiFetch } from './client'
import type { PortfolioSearchQuery, PortfolioSearchResult } from '@renderer/types/portfolioSearch'

/**
 * SC-11 비즈니스 매칭 데이터 소스 — `GET /portfolios` (API 명세서 §12.3).
 *
 * 인증 없이도 조회되는 엔드포인트다(BE `AuthSecurityConfig`가 permitAll로 열어 둔다) —
 * 공개 디렉터리(`/discover`)가 비로그인 화면이기 때문이다. 그래도 `apiFetch` 기본 동작
 * (토큰이 있으면 첨부)을 그대로 쓴다: 토큰이 있으면 서버가 본인 포트폴리오를 결과에서
 * 빼주므로 앱에서 볼 때 내 것이 섞이지 않는다.
 *
 * 응답 `PageResponse<PortfolioSearchItemResponse>`가 `PortfolioSearchResult`와 필드까지
 * 같아서 변환 없이 그대로 쓴다.
 */

/** 빈 값·NaN은 파라미터 자체를 보내지 않는다 — 서버 기본값(무제한)에 맡긴다. */
function putNumber(params: URLSearchParams, key: string, value: number | undefined): void {
  if (value === undefined || !Number.isFinite(value)) return
  params.set(key, String(value))
}

export async function searchPortfolios(
  query: PortfolioSearchQuery
): Promise<PortfolioSearchResult> {
  const params = new URLSearchParams()
  if (query.keyword) params.set('keyword', query.keyword)
  if (query.platform) params.set('platform', query.platform)
  if (query.tag) params.set('tag', query.tag)
  putNumber(params, 'minAvgViewers', query.minAvgViewers)
  putNumber(params, 'maxAvgViewers', query.maxAvgViewers)
  if (query.sort) params.set('sort', query.sort)
  params.set('page', String(query.page))
  params.set('size', String(query.size))

  return apiFetch<PortfolioSearchResult>(`/portfolios?${params}`)
}
