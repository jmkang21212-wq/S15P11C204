import type { LiveSnapshot } from '@renderer/types/console'

/**
 * 실시간 콘솔 스냅샷 조회.
 *
 * 현재는 mock을 반환한다. 실제 연동 시:
 *   const res = await fetch(`/api/v1/broadcast-sessions/${sessionId}/live-snapshot`, ...)
 *   return (await res.json()).data as LiveSnapshot
 * 이후 WebSocket(`/ws/v1/broadcast-sessions/{id}`)으로 briefings·sentiment 등을 갱신한다.
 */
export async function getLiveSnapshot(sessionId: number): Promise<LiveSnapshot> {
  await new Promise((resolve) => window.setTimeout(resolve, 200))
  return { ...MOCK_SNAPSHOT, session: { ...MOCK_SNAPSHOT.session, sessionId } }
}

// ─── mock 데이터 (실제 API 연동 시 제거) ───────────────────────────
const MOCK_SNAPSHOT: LiveSnapshot = {
  session: {
    sessionId: 9002,
    status: 'LIVE',
    startedAt: '2026-07-21T12:00:00Z',
    elapsedSeconds: 6452,
    currentViewers: 2417,
  },
  services: {
    microphone: 'CONNECTED',
    stt: 'RUNNING',
    ai: 'RUNNING',
  },
  briefings: [
    {
      briefingId: 504,
      briefingType: 'RISK',
      level: 'HIGH',
      message: '정치·비방 소지가 있는 표현이 감지됐습니다. 화제 전환을 권장합니다.',
      createdAt: '2026-07-21T13:47:00Z',
    },
    {
      briefingId: 503,
      briefingType: 'CHAT_SUMMARY',
      message: '최근 3분 채팅은 신규 챔피언 구매 문의가 다수이며, 긍정 반응이 우세합니다.',
      createdAt: '2026-07-21T13:44:00Z',
    },
    {
      briefingId: 502,
      briefingType: 'PROMISE',
      promiseId: 41,
      message: '“이번 판 이기면 구독자 추첨 이벤트 할게” — 약속으로 감지됐습니다.',
      createdAt: '2026-07-21T13:39:00Z',
    },
    {
      briefingId: 501,
      briefingType: 'FAQ',
      message: '“오늘 몇 시까지 하세요?” 반복 질문 — 저장된 답변: “오늘 새벽 1시까지 예정입니다”',
      createdAt: '2026-07-21T13:31:00Z',
    },
  ],
  sentiment: {
    positiveRatio: 0.64,
    neutralRatio: 0.27,
    negativeRatio: 0.09,
  },
  repeatedQuestions: [
    { questionId: 81, question: '오늘 몇 시까지 하세요?', occurrenceCount: 42 },
    { questionId: 82, question: '그 챔피언 왜 삼?', occurrenceCount: 31 },
    { questionId: 83, question: '마우스 뭐 쓰세요?', occurrenceCount: 24 },
    { questionId: 84, question: '내일도 방송해요?', occurrenceCount: 18 },
    { questionId: 85, question: '합방 언제 해요?', occurrenceCount: 15 },
  ],
  engagement: 78,
  lastEventId: '1753089600123-0',
}
