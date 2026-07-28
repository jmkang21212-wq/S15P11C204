import type { Portfolio } from '@renderer/types/portfolio'

/**
 * SC-10 포트폴리오 목 데이터.
 *
 * 백엔드 `GET/PATCH /portfolio/me`·`GET /portfolios/{publicSlug}`(명세서 §12)가 미구현이라 화면 개발용으로 둔다.
 * 실연동 후에는 `api/portfolio.ts`의 `USE_MOCK`만 끄면 되고 이 파일은 그대로 남겨 둔다.
 *
 * 다른 목과 달리 상수가 아니라 **가변 객체**다. 저장(PATCH)이 실제로 반영되어야
 * 편집 → 공개 뷰 흐름을 확인할 수 있기 때문이다. `api/portfolio.ts`에서만 건드린다.
 */
export const MOCK_PORTFOLIO: Portfolio = {
  portfolioId: 61,
  publicSlug: 'hanbyeol',
  title: '한별 TV',
  introduction:
    '신작과 도전형 콘텐츠를 중심으로 방송합니다. 시청자 참여 대회를 격주로 열고, 밤 9시부터 새벽 1시까지 꾸준히 켭니다.',
  contactEmail: 'business@example.com',
  contactUrl: 'https://example.com/contact',
  isPublic: true,
  publishedAt: '2026-07-20T08:00:00Z',
  profile: {
    nickname: '한별TV',
    profileImageUrl: 'https://cdn.example.com/profile/101.jpg',
    platformAccounts: [
      {
        platformAccountId: 15,
        platform: 'CHZZK',
        channelId: 'ch_12345',
        channelName: '한별TV'
      },
      {
        platformAccountId: 16,
        platform: 'SOOP',
        channelId: 'soop_988',
        channelName: '한별티비'
      }
    ]
  },
  tags: [
    { tagId: 1, name: 'FPS', tagType: 'CONTENT' },
    { tagId: 2, name: '롤토체스', tagType: 'CONTENT' },
    { tagId: 3, name: '저챗', tagType: 'CONTENT' }
  ],
  performance: {
    avgViewers: 2138,
    peakViewers: 3842,
    broadcastsPerWeek: 4,
    totalBroadcastCount: 142,
    totalChat: 524_300,
    primaryTimeRange: '21:00-01:00'
  },
  // SC-06·SC-07 목의 하이라이트와 같은 id를 써서 값이 어긋나지 않게 한다.
  featuredHighlights: [
    {
      highlightId: 71,
      displayOrder: 1,
      clipUrl: 'https://cdn.example.com/clips/71.mp4',
      thumbnailUrl: 'https://cdn.example.com/clips/71.jpg'
    },
    {
      highlightId: 73,
      displayOrder: 2,
      clipUrl: 'https://cdn.example.com/clips/73.mp4',
      thumbnailUrl: 'https://cdn.example.com/clips/73.jpg'
    }
  ]
}
