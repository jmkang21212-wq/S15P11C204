import { applyPortfolioSearch } from '@renderer/mocks/portfolioSearch.mock'
import type { PortfolioSearchQuery, PortfolioSearchResult } from '@renderer/types/portfolioSearch'

/**
 * SC-11 비즈니스 매칭 데이터 소스.
 *
 * 백엔드 미구현 상태 — 현재는 목 데이터를 반환한다.
 * 실연동 시 이 파일만 고치면 되고, 페이지·컴포넌트는 손대지 않는다.
 *
 * TODO: GET /portfolios 연동 (API 명세서 §12.3)
 *       `{ success, data }` 엔벨로프의 `data`를 반환하면 된다.
 *       쿼리스트링 직렬화(keyword·platform·tag·minAvgViewers·maxAvgViewers·sort·page·size)를 추가한다.
 */

/** 백엔드가 붙으면 false로 바꾸고 아래 fetch 분기를 채운다. */
const USE_MOCK = true

/** 목 응답에도 지연을 줘 로딩 스켈레톤이 실제로 보이게 한다. */
const MOCK_DELAY_MS = 400

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export async function searchPortfolios(
  query: PortfolioSearchQuery
): Promise<PortfolioSearchResult> {
  if (USE_MOCK) {
    await delay(MOCK_DELAY_MS)
    return applyPortfolioSearch(query)
  }

  // const params = new URLSearchParams()
  // if (query.keyword) params.set('keyword', query.keyword)
  // if (query.platform) params.set('platform', query.platform)
  // if (query.tag) params.set('tag', query.tag)
  // if (query.minAvgViewers !== undefined) params.set('minAvgViewers', String(query.minAvgViewers))
  // if (query.maxAvgViewers !== undefined) params.set('maxAvgViewers', String(query.maxAvgViewers))
  // if (query.sort) params.set('sort', query.sort)
  // params.set('page', String(query.page))
  // params.set('size', String(query.size))
  // const res = await fetch(`${BASE_URL}/portfolios?${params}`)
  // const body = await res.json()
  // return body.data
  throw new Error('NOT_IMPLEMENTED')
}
