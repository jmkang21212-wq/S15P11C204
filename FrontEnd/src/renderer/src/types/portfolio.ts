/**
 * SC-10 포트폴리오 도메인 타입.
 *
 * 출처: `weekly/2026_0720-0724/설계자료/API 명세서.md`
 *   §12.1 `GET /portfolio/me`
 *   §12.2 `PATCH /portfolio/me`
 *   §12.4 `GET /portfolios/{publicSlug}`
 */

export type Platform = 'CHZZK' | 'SOOP'

export interface PlatformAccount {
  platformAccountId: number
  platform: Platform
  channelId: string
  channelName: string
}

export interface PortfolioTag {
  tagId: number
  name: string
  tagType: string
}

export interface PortfolioProfile {
  nickname: string
  profileImageUrl: string | null
  platformAccounts: PlatformAccount[]
}

export interface PortfolioPerformance {
  avgViewers: number
  peakViewers: number
  broadcastsPerWeek: number
  totalBroadcastCount: number
  totalChat: number
  /** 최근 방송의 started_at·ended_at을 집계한 계산 필드 (예: "21:00-01:00") */
  primaryTimeRange: string
}

export interface FeaturedHighlight {
  highlightId: number
  displayOrder: number
  /** 클립 생성 전이거나 실패하면 null (명세서 §1.5) */
  clipUrl: string | null
  thumbnailUrl: string | null
}

export interface Portfolio {
  portfolioId: number
  /** 공개 주소에 쓰이는 슬러그. PATCH 대상이 아니라 읽기 전용이다. */
  publicSlug: string
  title: string
  introduction: string
  contactEmail: string | null
  contactUrl: string | null
  isPublic: boolean
  /** 아직 공개하지 않았으면 null (명세서 §1.5) */
  publishedAt: string | null
  profile: PortfolioProfile
  /** PATCH 본문에 없어 편집할 수 없다 — 읽기 전용으로만 표시한다. */
  tags: PortfolioTag[]
  performance: PortfolioPerformance
  featuredHighlights: FeaturedHighlight[]
}

/**
 * 공개 뷰 응답 — 편집용 상태는 제외된다 (명세서 §12.4).
 * 화면에서도 이 타입을 써서 편집 전용 필드가 새어 나가지 않게 한다.
 */
export type PublicPortfolio = Omit<Portfolio, 'isPublic' | 'publishedAt'>

/** PATCH 본문. 전달한 필드만 수정된다. 태그·publicSlug는 대상이 아니다. */
export interface PortfolioUpdate {
  title?: string
  introduction?: string
  contactEmail?: string | null
  contactUrl?: string | null
  isPublic?: boolean
  featuredHighlights?: { highlightId: number; displayOrder: number }[]
}
