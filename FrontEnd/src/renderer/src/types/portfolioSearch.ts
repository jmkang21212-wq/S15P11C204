import type { Platform, PlatformAccount } from '@renderer/types/portfolio'

/**
 * SC-11 비즈니스 매칭 도메인 타입.
 *
 * 출처: `weekly/2026_0720-0724/설계자료/API 명세서.md` §12.3 `GET /portfolios`
 * §1.2에 명시된 대로 API 기능은 공개 포트폴리오 탐색으로 한정된다 —
 * 매칭 요청·추천 점수·수락/거절은 이번 개정에서 제외됐다.
 */

export type PortfolioSort = 'RECENT' | 'AVG_VIEWERS_DESC' | 'BROADCASTS_DESC'

export interface PortfolioSearchQuery {
  keyword?: string
  platform?: Platform
  tag?: string
  minAvgViewers?: number
  maxAvgViewers?: number
  sort?: PortfolioSort
  page: number
  size: number
}

export interface PortfolioSearchItem {
  portfolioId: number
  publicSlug: string
  nickname: string
  profileImageUrl: string | null
  platformAccounts: PlatformAccount[]
  /** SC-10의 `tags`(객체 배열)와 달리 문자열 배열이다. */
  categoryTags: string[]
  avgViewers: number
  broadcastsPerWeek: number
}

export interface PortfolioSearchResult {
  items: PortfolioSearchItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
