import { apiFetch } from './client'
import { MOCK_ENDED_SESSIONS } from '@renderer/mocks/sessionReport.mock'
import type {
  BroadcastSessionDetail,
  BroadcastSessionListItem,
  BroadcastStatus,
  EndBroadcastSessionResult,
  Page,
  StartBroadcastSessionInput,
  StartBroadcastSessionResult,
  VoiceTicket
} from '@renderer/types/broadcastSession'

/**
 * 방송 세션 API — `POST/GET /api/v1/broadcast-sessions` (BroadcastSessionController).
 * 세션 리포트(SC-06)·클립(SC-07)·콘솔(SC-03)·대시보드(SC-02)가 함께 쓴다.
 * 전 엔드포인트가 Bearer JWT를 요구한다 (api/client.ts가 자동 주입·401 재시도).
 */

/** 목으로 되돌리려면 true. 삭제 시 각 함수 상단 3줄만 지우면 된다. */
const USE_MOCK = false

/** 목 응답에도 지연을 줘 로딩 스켈레톤이 실제로 보이게 한다. */
const MOCK_DELAY_MS = 400

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

const BASE = '/broadcast-sessions'

/**
 * 진행 중(PREPARING·LIVE) 세션. 없으면 null.
 * BE는 없을 때 204를 반환하고, apiFetch는 204를 undefined로 돌려준다.
 */
export async function fetchCurrentSession(): Promise<BroadcastSessionDetail | null> {
  if (USE_MOCK) {
    await delay(MOCK_DELAY_MS)
    return null
  }
  const data = await apiFetch<BroadcastSessionDetail | undefined>(`${BASE}/current`)
  return data ?? null
}

export async function fetchSessionDetail(sessionId: number): Promise<BroadcastSessionDetail> {
  if (USE_MOCK) {
    await delay(MOCK_DELAY_MS)
    throw new Error('NOT_IMPLEMENTED')
  }
  return apiFetch<BroadcastSessionDetail>(`${BASE}/${sessionId}`)
}

/** 0-based page, size 1~100. status는 BE BroadcastStatus enum 이름과 같다. */
export async function fetchSessions(params?: {
  page?: number
  size?: number
  status?: BroadcastStatus
}): Promise<Page<BroadcastSessionListItem>> {
  if (USE_MOCK) {
    await delay(MOCK_DELAY_MS)
    return { items: MOCK_ENDED_SESSIONS, page: 0, size: 50, totalElements: 0, totalPages: 1 }
  }
  const query = new URLSearchParams()
  if (params?.page !== undefined) query.set('page', String(params.page))
  if (params?.size !== undefined) query.set('size', String(params.size))
  if (params?.status !== undefined) query.set('status', params.status)
  const qs = query.toString()
  return apiFetch<Page<BroadcastSessionListItem>>(`${BASE}${qs ? `?${qs}` : ''}`)
}

/**
 * 종료된 방송 세션 목록. 최근 순으로 정렬되어 있다. 세션 리포트(SC-06)와 클립(SC-07)이 함께 쓴다.
 * 두 화면 다 페이지네이션 UI가 없어 size:50 1페이지 고정.
 * TODO: 무한 스크롤 도입 시 fetchSessions를 직접 쓴다.
 */
export async function fetchEndedSessions(): Promise<BroadcastSessionListItem[]> {
  if (USE_MOCK) {
    await delay(MOCK_DELAY_MS)
    return MOCK_ENDED_SESSIONS
  }
  const page = await fetchSessions({ page: 0, size: 50, status: 'ENDED' })
  return page.items
}

/** 201 Created. PREPARING 세션과 준비 Outbox 이벤트가 생성된다. */
export async function startSession(
  input: StartBroadcastSessionInput
): Promise<StartBroadcastSessionResult> {
  if (USE_MOCK) {
    await delay(MOCK_DELAY_MS)
    throw new Error('NOT_IMPLEMENTED')
  }
  return apiFetch<StartBroadcastSessionResult>(BASE, {
    method: 'POST',
    body: JSON.stringify(input)
  })
}

/** 202 Accepted. 리포트 생성은 비동기로 예약되므로 reportStatus는 대개 PROCESSING이다. */
export async function endSession(sessionId: number): Promise<EndBroadcastSessionResult> {
  if (USE_MOCK) {
    await delay(MOCK_DELAY_MS)
    throw new Error('NOT_IMPLEMENTED')
  }
  return apiFetch<EndBroadcastSessionResult>(`${BASE}/${sessionId}/end`, { method: 'POST' })
}

/**
 * 음성 AI WebSocket 접속용 일회용 티켓을 발급받는다.
 *
 * WebSocket은 Authorization 헤더를 실을 수 없어 JWT를 직접 못 쓴다. 인증된 이 요청으로
 * 티켓을 받아 핸드셰이크에 실으면, AI 서버가 Redis에서 조회해 어느 방송 세션인지
 * 확인한다. 한 번 쓰면 소비되므로 **재연결할 때마다** 다시 불러야 한다.
 */
export async function issueVoiceTicket(sessionId: number): Promise<VoiceTicket> {
  return apiFetch<VoiceTicket>(`${BASE}/${sessionId}/voice-ticket`, { method: 'POST' })
}
