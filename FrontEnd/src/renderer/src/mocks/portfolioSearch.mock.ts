import type {
  PortfolioSearchItem,
  PortfolioSearchQuery,
  PortfolioSearchResult
} from '@renderer/types/portfolioSearch'

/**
 * SC-11 비즈니스 매칭 목 데이터.
 *
 * 백엔드 `GET /portfolios`(명세서 §12.3)가 미구현이라 화면 개발용으로 둔다.
 * 실연동 후에는 `api/portfolioSearch.ts`의 `USE_MOCK`만 끄면 되고 이 파일은 그대로 남겨 둔다.
 *
 * 로그인 사용자(`mocks/portfolio.mock.ts`의 `hanbyeol`)는 이 디렉터리에 넣지 않는다 —
 * 명세서의 "로그인한 스트리머 자신은 제외" 규칙을 인증 상태 없이 흉내 내는 대신,
 * 애초에 검색 대상에서 빼는 방식으로 단순화했다.
 */

interface Seed {
  nickname: string
  platform: 'CHZZK' | 'SOOP'
  tags: string[]
  avgViewers: number
  broadcastsPerWeek: number
}

/** 태그·시청자 규모·플랫폼을 고르게 섞어 필터·정렬이 실제로 값을 바꿔 보이게 한다. */
const SEEDS: Seed[] = [
  {
    nickname: '달빛게임즈',
    platform: 'CHZZK',
    tags: ['FPS', '발로란트'],
    avgViewers: 4820,
    broadcastsPerWeek: 5
  },
  {
    nickname: '레인보우스텔라',
    platform: 'SOOP',
    tags: ['버라이어티', '저챗'],
    avgViewers: 1120,
    broadcastsPerWeek: 3
  },
  {
    nickname: '체스마스터민',
    platform: 'CHZZK',
    tags: ['롤토체스'],
    avgViewers: 3260,
    broadcastsPerWeek: 4
  },
  {
    nickname: '노래하는곰돌',
    platform: 'SOOP',
    tags: ['음악', '저챗'],
    avgViewers: 860,
    broadcastsPerWeek: 2
  },
  {
    nickname: '스나이퍼유진',
    platform: 'CHZZK',
    tags: ['FPS', '배틀그라운드'],
    avgViewers: 2740,
    broadcastsPerWeek: 4
  },
  {
    nickname: '심야라디오J',
    platform: 'SOOP',
    tags: ['저챗', '토크'],
    avgViewers: 540,
    broadcastsPerWeek: 5
  },
  {
    nickname: '길드마스터혜온',
    platform: 'CHZZK',
    tags: ['MMORPG', '롤토체스'],
    avgViewers: 1980,
    broadcastsPerWeek: 3
  },
  {
    nickname: '픽셀아트다인',
    platform: 'SOOP',
    tags: ['인디게임', '버라이어티'],
    avgViewers: 720,
    broadcastsPerWeek: 2
  },
  {
    nickname: '탑랭커소미',
    platform: 'CHZZK',
    tags: ['FPS', '발로란트'],
    avgViewers: 5140,
    broadcastsPerWeek: 6
  },
  {
    nickname: '캠핑브이로그린',
    platform: 'SOOP',
    tags: ['버라이어티'],
    avgViewers: 980,
    broadcastsPerWeek: 2
  },
  {
    nickname: '조합연구소재인',
    platform: 'CHZZK',
    tags: ['롤토체스'],
    avgViewers: 2410,
    broadcastsPerWeek: 4
  },
  {
    nickname: '기타치는솔',
    platform: 'SOOP',
    tags: ['음악'],
    avgViewers: 1340,
    broadcastsPerWeek: 3
  },
  {
    nickname: '헤드샷하니',
    platform: 'CHZZK',
    tags: ['FPS', '배틀그라운드'],
    avgViewers: 3620,
    broadcastsPerWeek: 5
  },
  {
    nickname: '보드게임덕후',
    platform: 'SOOP',
    tags: ['버라이어티', '보드게임'],
    avgViewers: 610,
    broadcastsPerWeek: 2
  },
  {
    nickname: '새벽챗봇운영자',
    platform: 'CHZZK',
    tags: ['저챗', '토크'],
    avgViewers: 450,
    broadcastsPerWeek: 4
  },
  {
    nickname: '랭커즈길드장',
    platform: 'SOOP',
    tags: ['MMORPG'],
    avgViewers: 1760,
    broadcastsPerWeek: 3
  },
  {
    nickname: '초시계레이싱',
    platform: 'CHZZK',
    tags: ['레이싱', '버라이어티'],
    avgViewers: 2050,
    broadcastsPerWeek: 3
  },
  {
    nickname: '요리하는은지',
    platform: 'SOOP',
    tags: ['버라이어티', '요리'],
    avgViewers: 1450,
    broadcastsPerWeek: 4
  },
  {
    nickname: '전략의신태오',
    platform: 'CHZZK',
    tags: ['롤토체스', 'MMORPG'],
    avgViewers: 2900,
    broadcastsPerWeek: 4
  },
  {
    nickname: '통기타밤하늘',
    platform: 'SOOP',
    tags: ['음악', '저챗'],
    avgViewers: 780,
    broadcastsPerWeek: 3
  },
  {
    nickname: '클러치퀸다혜',
    platform: 'CHZZK',
    tags: ['FPS', '발로란트'],
    avgViewers: 4310,
    broadcastsPerWeek: 5
  },
  {
    nickname: '한밤중독서실',
    platform: 'SOOP',
    tags: ['저챗'],
    avgViewers: 320,
    broadcastsPerWeek: 3
  },
  {
    nickname: '픽셀농장주인',
    platform: 'CHZZK',
    tags: ['인디게임', 'MMORPG'],
    avgViewers: 1590,
    broadcastsPerWeek: 2
  },
  {
    nickname: '드리프트킹환',
    platform: 'SOOP',
    tags: ['레이싱'],
    avgViewers: 2230,
    broadcastsPerWeek: 4
  },
  {
    nickname: '보이스액터리아',
    platform: 'CHZZK',
    tags: ['버라이어티', '토크'],
    avgViewers: 1870,
    broadcastsPerWeek: 3
  },
  {
    nickname: '배그헤비유저',
    platform: 'SOOP',
    tags: ['FPS', '배틀그라운드'],
    avgViewers: 3050,
    broadcastsPerWeek: 5
  }
]

/**
 * SEEDS를 실제 응답 모양으로 확장한다. id는 SC-10 목(61)과 겹치지 않게 200번대부터 매긴다.
 *
 * 이미지 URL은 `null`로 둔다. 예전에는 `cdn.example.com`을 가리켰는데 존재하지 않는 도메인이라
 * 목록을 열 때마다 `ERR_NAME_NOT_RESOLVED`가 항목 수만큼 콘솔에 쏟아져 실제 오류를 덮었다.
 * `null`은 실제 응답에서도 정상 값이고(`profileImageUrl: string | null`), `Avatar`의 폴백
 * 아이콘 경로를 함께 확인할 수 있다.
 */
export const MOCK_PORTFOLIO_DIRECTORY: PortfolioSearchItem[] = SEEDS.map((seed, index) => {
  const portfolioId = 200 + index
  const platformAccountId = 300 + index
  return {
    portfolioId,
    publicSlug: `streamer-${portfolioId}`,
    nickname: seed.nickname,
    profileImageUrl: null,
    platformAccounts: [
      {
        platformAccountId,
        platform: seed.platform,
        channelId: `ch_${portfolioId}`,
        channelName: seed.nickname
      }
    ],
    categoryTags: seed.tags,
    avgViewers: seed.avgViewers,
    broadcastsPerWeek: seed.broadcastsPerWeek
  }
})

/**
 * 목 디렉터리에 명세서 §12.3의 필터·정렬·페이지네이션을 그대로 적용한다.
 * `api/portfolioSearch.ts`가 이 함수만 호출하므로 검색 로직이 api 파일을 어지럽히지 않는다.
 */
export function applyPortfolioSearch(query: PortfolioSearchQuery): PortfolioSearchResult {
  let items = MOCK_PORTFOLIO_DIRECTORY

  if (query.keyword) {
    const keyword = query.keyword.trim().toLowerCase()
    if (keyword) {
      items = items.filter(
        (item) =>
          item.nickname.toLowerCase().includes(keyword) ||
          item.platformAccounts.some((a) => a.channelName.toLowerCase().includes(keyword)) ||
          item.categoryTags.some((tag) => tag.toLowerCase().includes(keyword))
      )
    }
  }

  if (query.platform) {
    items = items.filter((item) => item.platformAccounts.some((a) => a.platform === query.platform))
  }

  if (query.tag) {
    const tag = query.tag.trim()
    if (tag) {
      items = items.filter((item) => item.categoryTags.includes(tag))
    }
  }

  if (query.minAvgViewers !== undefined) {
    items = items.filter((item) => item.avgViewers >= query.minAvgViewers!)
  }
  if (query.maxAvgViewers !== undefined) {
    items = items.filter((item) => item.avgViewers <= query.maxAvgViewers!)
  }

  const sorted = [...items]
  switch (query.sort) {
    case 'AVG_VIEWERS_DESC':
      sorted.sort((a, b) => b.avgViewers - a.avgViewers)
      break
    case 'BROADCASTS_DESC':
      sorted.sort((a, b) => b.broadcastsPerWeek - a.broadcastsPerWeek)
      break
    case 'RECENT':
    default:
      // 목 디렉터리 자체가 최신순으로 정렬돼 있다고 가정한다 (portfolioId 내림차순).
      sorted.sort((a, b) => b.portfolioId - a.portfolioId)
  }

  const totalElements = sorted.length
  const totalPages = Math.max(1, Math.ceil(totalElements / query.size))
  const start = query.page * query.size
  const pageItems = sorted.slice(start, start + query.size)

  return {
    items: pageItems,
    page: query.page,
    size: query.size,
    totalElements,
    totalPages
  }
}
