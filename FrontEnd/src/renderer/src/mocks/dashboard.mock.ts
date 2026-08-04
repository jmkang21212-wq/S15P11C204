import type { DashboardData } from '@renderer/types/dashboard'

/**
 * 대시보드 목 데이터 — `api/dashboard.ts`의 `USE_MOCK`이 true일 때만 쓰인다.
 * 백엔드가 내려주는 형태와 같은 계약을 따르므로 화면 코드는 목/실제를 구분하지 않는다.
 */
export const MOCK_DASHBOARD: DashboardData = {
  upcomingBroadcast: {
    // 실제 calendar_events에 없는 임의 값이면 세션 시작(POST /broadcast-sessions)이
    // CALENDAR_EVENT_NOT_FOUND로 거부된다. 목에서는 연결할 실제 일정이 없으므로 null로 둔다
    // — "일정 없이 즉석 시작"과 같은 경로라 세션 자체는 정상 생성된다.
    calendarEventId: null,
    title: '발로란트 랭크 정복 Day 12',
    startAt: '2026-07-21T12:00:00Z',
    endAt: '2026-07-21T15:00:00Z',
    status: 'SCHEDULED'
  },
  recentSession: {
    sessionId: 9001,
    title: '심야 수다방 & 롤토체스',
    endedAt: '2026-07-20T16:32:00Z',
    reportStatus: 'COMPLETED',
    avgViewers: 2105,
    peakViewers: 3842,
    summary: '롤토체스 구간에서 시청자 유입이 집중되었습니다.'
  },
  pendingPromiseCount: 5,
  pendingPromises: [
    {
      promiseId: 31,
      sessionId: 9001,
      content: '구독자 500명 달성 시 게임 추가',
      status: 'PENDING',
      dueDate: '2026-07-26',
      detectedAt: '2026-07-20T14:43:00Z'
    },
    {
      promiseId: 32,
      sessionId: 9001,
      content: '다음 주 합방 일정 공지하기',
      status: 'ON_HOLD',
      dueDate: null,
      detectedAt: '2026-07-18T13:10:00Z'
    },
    {
      promiseId: 33,
      sessionId: 9001,
      content: '지난 방송 하이라이트 클립 올리기',
      status: 'PENDING',
      dueDate: '2026-07-28',
      detectedAt: '2026-07-19T22:05:00Z'
    }
  ],
  weeklyMetrics: {
    periodStart: '2026-07-14',
    periodEnd: '2026-07-20',
    avgViewers: 2138,
    avgViewersChangeRate: 0.12,
    totalChat: 48200,
    totalChatChangeRate: 0.08
  },
  recentHighlights: [
    { highlightId: 71, durationMs: 42000, clipUrl: null, thumbnailUrl: null },
    { highlightId: 72, durationMs: 68000, clipUrl: null, thumbnailUrl: null },
    { highlightId: 73, durationMs: 55000, clipUrl: null, thumbnailUrl: null },
    { highlightId: 74, durationMs: 91000, clipUrl: null, thumbnailUrl: null },
    { highlightId: 75, durationMs: 38000, clipUrl: null, thumbnailUrl: null }
  ]
}
