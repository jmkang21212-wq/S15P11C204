/**
 * SC-10 포트폴리오 도메인 타입.
 *
 * 출처: `weekly/2026_0720-0724/설계자료/API 명세서.md`
 *   §12.1 `GET /portfolio/me`
 *   §12.2 `PATCH /portfolio/me`
 *   §12.4 `GET /portfolios/{publicSlug}`
 */

export type Platform = 'CHZZK' | 'SOOP'

/** SC-10 포트폴리오와 SC-11 검색 카드가 함께 쓰는 플랫폼 표기. */
export const PLATFORM_LABEL: Record<Platform, string> = {
  CHZZK: '치지직',
  SOOP: 'SOOP'
}

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

/**
 * POST 본문 (`POST /portfolio/me`).
 *
 * 생성은 자동 구성이 아니라 "사용자가 설정을 마치는 행위"라, 백엔드가 대신 채워주는
 * 기본값이 없다 — `title`·`isPublic`·`tagIds`·`featuredHighlights`가 모두 필수다.
 * `tagIds`는 1개 이상이어야 하고, `featuredHighlights`는 배열 자체는 필수지만 0개를 허용한다
 * (클립이 안 뽑힌 방송도 있어 1개 이상을 강제하면 생성이 막히는 스트리머가 생긴다).
 *
 * `publicSlug`는 서버가 무작위로 만든다 — 공개 주소라 추측 불가능해야 해서 요청에 없다.
 */
export interface PortfolioCreate {
  title: string
  /** 비워도 되지만 null은 보내지 않는다 — 조회 시 `introduction`을 non-null로 다루기 때문이다. */
  introduction: string
  contactEmail: string | null
  contactUrl: string | null
  isPublic: boolean
  tagIds: number[]
  featuredHighlights: { highlightId: number; displayOrder: number }[]
}

/** PATCH 본문. 전달한 필드만 수정된다. 태그·publicSlug는 대상이 아니다. */
export interface PortfolioUpdate {
  title?: string
  introduction?: string
  contactEmail?: string | null
  contactUrl?: string | null
  isPublic?: boolean
  featuredHighlights?: { highlightId: number; displayOrder: number }[]
}
