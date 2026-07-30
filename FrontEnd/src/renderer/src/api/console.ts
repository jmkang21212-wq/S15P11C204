import { apiFetch } from './client'
import type {
  Briefing,
  LiveSnapshot,
  LiveSnapshotSession,
  Sentiment
} from '@renderer/types/console'

/**
 * 실시간 콘솔 스냅샷 — `LiveConsoleController`(`GET /broadcast-sessions/{id}/live-snapshot`).
 * BE 결정(2026-07-29)으로 WebSocket이 아니라 2~3초 폴링 전제다. 호출부(`BroadcastConsolePage`
 * 등)가 그 주기로 이 함수를 반복 호출한다.
 *
 * `briefings`·`repeatedQuestions`는 STT·반복 질문 파이프라인이 아직 없어 BE가 항상 빈
 * 배열을 준다 — 실시간 브리핑은 `useAudioStream`/`useLiveBriefings`(AI 서버 WebSocket
 * 중계)에서 별도로 오고, 호출부가 스냅샷 값과 합쳐서 쓴다.
 */
const USE_MOCK = false
const MOCK_DELAY_MS = 200

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export async function getLiveSnapshot(sessionId: number): Promise<LiveSnapshot> {
  if (USE_MOCK) {
    await delay(MOCK_DELAY_MS)
    return { ...MOCK_SNAPSHOT, session: { ...MOCK_SNAPSHOT.session, sessionId } }
  }

  const raw = await apiFetch<RawLiveSnapshot>(`/broadcast-sessions/${sessionId}/live-snapshot`)
  return {
    // status는 BroadcastStatus와 같은 값 집합(BroadcastStatus enum name)이지만 wire 타입은
    // 느슨한 string이라 단언이 필요하다.
    session: raw.session as LiveSnapshotSession,
    services: raw.services,
    // BE는 STT 파이프라인이 없어 항상 빈 배열을 준다 — briefingType 유니온 단언도 그래서 안전하다.
    briefings: raw.briefings as Briefing[],
    sentiment: toSentiment(raw.sentiment),
    repeatedQuestions: raw.repeatedQuestions,
    // BE는 0~1 비율(유니크 채터 ÷ 시청자)을 준다. 오버레이 게이지는 0~100 폭으로 그린다.
    engagement: Math.round((raw.sentiment.engagement ?? 0) * 100),
    lastEventId: raw.lastEventId
  }
}

// ─── BE 응답(wire) 타입 — LiveSnapshotResponse와 1:1, 이 파일 밖에서는 쓰지 않는다 ───
interface RawSession {
  sessionId: number
  status: string
  startedAt: string
  elapsedSeconds: number
  currentViewers: number
}
interface RawServices {
  microphone: string
  stt: string
  ai: string
  collector: string
}
interface RawBriefing {
  briefingId: number
  briefingType: string
  message: string
  createdAt: string
}
interface RawSentiment {
  windowSec: number
  analyzedChat: number
  engagement: number | null
  dominant: string | null
  /** 감정 코드(SentimentLabel.name()) → 비율(합 1.0). analyzedChat이 0이면 null. */
  distribution: Record<string, number> | null
}
interface RawRepeatedQuestion {
  questionId: number
  question: string
  occurrenceCount: number
}
interface RawLiveSnapshot {
  session: RawSession
  services: RawServices
  briefings: RawBriefing[]
  sentiment: RawSentiment
  repeatedQuestions: RawRepeatedQuestion[]
  lastEventId: string
}

/**
 * BE의 감정 8종(`SentimentLabel`) 분포를 화면의 긍정/중립/부정 3분할로 묶는다.
 * SURPRISE(놀람)는 방향성이 불분명해 중립 쪽에 둔다.
 * analyzedChat이 0(아직 분석된 채팅 없음)이면 BE가 null을 주므로, 값이 없다는 뜻으로
 * 중립 100%를 표시한다(전부 0%로 두면 "매우 부정적으로 중립"처럼 읽혀 오해의 소지가 있다).
 */
const POSITIVE_LABELS = ['JOY', 'EXCITEMENT', 'SUPPORT']
const NEGATIVE_LABELS = ['SADNESS', 'ANGER']

function toSentiment(raw: RawSentiment): Sentiment {
  if (!raw.distribution || raw.analyzedChat === 0) {
    return { positiveRatio: 0, neutralRatio: 1, negativeRatio: 0 }
  }
  const sumOf = (labels: string[]): number =>
    labels.reduce((total, label) => total + (raw.distribution?.[label] ?? 0), 0)
  const positiveRatio = sumOf(POSITIVE_LABELS)
  const negativeRatio = sumOf(NEGATIVE_LABELS)
  const neutralRatio = Math.max(0, 1 - positiveRatio - negativeRatio)
  return { positiveRatio, neutralRatio, negativeRatio }
}

// ─── mock 데이터 (되돌릴 때 USE_MOCK = true) ───────────────────────────
const MOCK_SNAPSHOT: LiveSnapshot = {
  session: {
    sessionId: 9002,
    status: 'LIVE',
    startedAt: '2026-07-21T12:00:00Z',
    elapsedSeconds: 6452,
    currentViewers: 2417
  },
  services: {
    microphone: 'CONNECTED',
    stt: 'RUNNING',
    ai: 'RUNNING',
    collector: 'RUNNING'
  },
  briefings: [
    {
      briefingId: 504,
      briefingType: 'RISK',
      level: 'HIGH',
      message: '정치·비방 소지가 있는 표현이 감지됐습니다. 화제 전환을 권장합니다.',
      createdAt: '2026-07-21T13:47:00Z'
    },
    {
      briefingId: 503,
      briefingType: 'CHAT_SUMMARY',
      message: '최근 3분 채팅은 신규 챔피언 구매 문의가 다수이며, 긍정 반응이 우세합니다.',
      createdAt: '2026-07-21T13:44:00Z'
    },
    {
      briefingId: 502,
      briefingType: 'PROMISE',
      promiseId: 41,
      message: '“이번 판 이기면 구독자 추첨 이벤트 할게” — 약속으로 감지됐습니다.',
      createdAt: '2026-07-21T13:39:00Z'
    },
    {
      briefingId: 501,
      briefingType: 'FAQ',
      message: '“오늘 몇 시까지 하세요?” 반복 질문 — 저장된 답변: “오늘 새벽 1시까지 예정입니다”',
      createdAt: '2026-07-21T13:31:00Z'
    }
  ],
  sentiment: {
    positiveRatio: 0.64,
    neutralRatio: 0.27,
    negativeRatio: 0.09
  },
  repeatedQuestions: [
    { questionId: 81, question: '오늘 몇 시까지 하세요?', occurrenceCount: 42 },
    { questionId: 82, question: '그 챔피언 왜 삼?', occurrenceCount: 31 },
    { questionId: 83, question: '마우스 뭐 쓰세요?', occurrenceCount: 24 },
    { questionId: 84, question: '내일도 방송해요?', occurrenceCount: 18 },
    { questionId: 85, question: '합방 언제 해요?', occurrenceCount: 15 }
  ],
  engagement: 78,
  lastEventId: '1753089600123-0'
}
