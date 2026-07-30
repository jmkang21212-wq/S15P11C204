import type { DashboardData } from '@renderer/types/dashboard'

/**
 * 대시보드 데이터 조회.
 *
 * 현재는 백엔드 미연결 상태라 mock을 반환한다.
 * 실제 연동 시 아래 본문만 교체하면 되고, 화면/훅 코드는 바뀌지 않는다:
 *
 *   const res = await fetch('/api/v1/dashboard', { headers: { Authorization: `Bearer ${token}` } })
 *   const json = await res.json()   // { success, data }
 *   return json.data as DashboardData
 */
export async function getDashboard(): Promise<DashboardData> {
  // 네트워크 지연 흉내 (로딩 상태 확인용)
  await new Promise((resolve) => window.setTimeout(resolve, 300))
  return MOCK_DASHBOARD
}

// ─── 아래는 mock 데이터 (실제 API 연동 시 제거) ───────────────────────────
const MOCK_DASHBOARD: DashboardData = {
  upcomingBroadcast: {
    // 실제 calendar_events 테이블에 존재하지 않는 임의 값이면 세션 시작(POST /broadcast-sessions)이
    // CALENDAR_EVENT_NOT_FOUND로 거부된다. 목 데이터일 뿐이라 실제 일정과 연결할 수 없으므로
    // null로 둔다 — "일정 없이 즉석 시작"과 같은 경로라 세션 자체는 정상 생성된다.
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
    { highlightId: 71, durationMs: 42000, clipUrl: '', thumbnailUrl: '' },
    { highlightId: 72, durationMs: 68000, clipUrl: '', thumbnailUrl: '' },
    { highlightId: 73, durationMs: 55000, clipUrl: '', thumbnailUrl: '' },
    { highlightId: 74, durationMs: 91000, clipUrl: '', thumbnailUrl: '' },
    { highlightId: 75, durationMs: 38000, clipUrl: '', thumbnailUrl: '' }
  ]
}
