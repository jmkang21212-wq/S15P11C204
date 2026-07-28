import { MOCK_PORTFOLIO } from '@renderer/mocks/portfolio.mock'
import type { Portfolio, PortfolioUpdate, PublicPortfolio } from '@renderer/types/portfolio'

/**
 * SC-10 포트폴리오 데이터 소스.
 *
 * 백엔드 미구현 상태 — 현재는 목 데이터를 반환한다.
 * 실연동 시 이 파일만 고치면 되고, 페이지·컴포넌트는 손대지 않는다.
 *
 * TODO: GET   /portfolio/me              연동 (API 명세서 §12.1)
 * TODO: PATCH /portfolio/me              연동 (API 명세서 §12.2)
 * TODO: GET   /portfolios/{publicSlug}   연동 (API 명세서 §12.4, 인증 불필요)
 *       모두 `{ success, data }` 엔벨로프이므로 `data`를 그대로 반환하면 된다.
 */

/** 백엔드가 붙으면 false로 바꾸고 아래 fetch 분기를 채운다. */
const USE_MOCK = true

/** 목 응답에도 지연을 줘 로딩 스켈레톤이 실제로 보이게 한다. */
const MOCK_DELAY_MS = 400

/** 비공개이거나 존재하지 않는 포트폴리오 — 명세서의 404에 대응한다. */
export const PORTFOLIO_NOT_PUBLIC = 'PORTFOLIO_NOT_PUBLIC'

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/** 목 상태. 저장이 실제로 반영되어야 편집 → 공개 뷰 흐름을 확인할 수 있다. */
let mockPortfolio: Portfolio = MOCK_PORTFOLIO

/**
 * 공개 응답으로 변환한다. 편집 전용 상태(`isPublic`·`publishedAt`)를 빼고
 * 내보낼 필드만 나열해, 무엇이 공개되는지 코드에서 바로 읽히게 한다 (명세서 §12.4).
 */
function toPublic(portfolio: Portfolio): PublicPortfolio {
  return {
    portfolioId: portfolio.portfolioId,
    publicSlug: portfolio.publicSlug,
    title: portfolio.title,
    introduction: portfolio.introduction,
    contactEmail: portfolio.contactEmail,
    contactUrl: portfolio.contactUrl,
    profile: portfolio.profile,
    tags: portfolio.tags,
    performance: portfolio.performance,
    featuredHighlights: portfolio.featuredHighlights
  }
}

export async function fetchMyPortfolio(): Promise<Portfolio> {
  if (USE_MOCK) {
    await delay(MOCK_DELAY_MS)
    return mockPortfolio
  }

  // const res = await fetch(`${BASE_URL}/portfolio/me`)
  // const body = await res.json()
  // return body.data
  throw new Error('NOT_IMPLEMENTED')
}

export async function updateMyPortfolio(patch: PortfolioUpdate): Promise<Portfolio> {
  if (USE_MOCK) {
    await delay(MOCK_DELAY_MS)
    // 서버가 병합해 돌려주는 동작을 흉내 낸다. featuredHighlights는 id·순서만 오므로
    // 기존 항목에서 clipUrl·thumbnailUrl을 찾아 채운다.
    const nextHighlights = patch.featuredHighlights?.map((item) => {
      const known = mockPortfolio.featuredHighlights.find((h) => h.highlightId === item.highlightId)
      return {
        highlightId: item.highlightId,
        displayOrder: item.displayOrder,
        clipUrl: known?.clipUrl ?? null,
        thumbnailUrl: known?.thumbnailUrl ?? null
      }
    })

    mockPortfolio = {
      ...mockPortfolio,
      title: patch.title ?? mockPortfolio.title,
      introduction: patch.introduction ?? mockPortfolio.introduction,
      contactEmail:
        patch.contactEmail !== undefined ? patch.contactEmail : mockPortfolio.contactEmail,
      contactUrl: patch.contactUrl !== undefined ? patch.contactUrl : mockPortfolio.contactUrl,
      isPublic: patch.isPublic ?? mockPortfolio.isPublic,
      featuredHighlights: nextHighlights ?? mockPortfolio.featuredHighlights
    }
    return mockPortfolio
  }

  // const res = await fetch(`${BASE_URL}/portfolio/me`, {
  //   method: 'PATCH',
  //   headers: { 'Content-Type': 'application/json' },
  //   body: JSON.stringify(patch)
  // })
  // const body = await res.json()
  // return body.data
  throw new Error('NOT_IMPLEMENTED')
}

/**
 * 공개 포트폴리오 조회. 인증이 필요 없고, 비공개면 404다.
 * 편집용 상태(isPublic·publishedAt)는 응답에서 제외된다 (명세서 §12.4).
 */
export async function fetchPublicPortfolio(publicSlug: string): Promise<PublicPortfolio> {
  if (USE_MOCK) {
    await delay(MOCK_DELAY_MS)
    if (publicSlug !== mockPortfolio.publicSlug || !mockPortfolio.isPublic) {
      throw new Error(PORTFOLIO_NOT_PUBLIC)
    }
    return toPublic(mockPortfolio)
  }

  // const res = await fetch(`${BASE_URL}/portfolios/${publicSlug}`)
  // if (res.status === 404) throw new Error(PORTFOLIO_NOT_PUBLIC)
  // const body = await res.json()
  // return body.data
  throw new Error('NOT_IMPLEMENTED')
}
