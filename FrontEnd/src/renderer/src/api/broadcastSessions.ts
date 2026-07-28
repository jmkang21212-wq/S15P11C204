import { MOCK_ENDED_SESSIONS } from '@renderer/mocks/sessionReport.mock'
import type { EndedSessionSummary } from '@renderer/types/report'

/**
 * 방송 세션 조회 — 세션 리포트(SC-06)와 클립(SC-07)이 함께 쓴다.
 *
 * 백엔드 미구현 상태 — 현재는 목 데이터를 반환한다.
 * TODO: GET /broadcast-sessions?page&size&status=ENDED 연동 (API 명세서 §10.1)
 *       `{ success, data }` 엔벨로프의 `data.content`를 반환하면 된다.
 */

/** 백엔드가 붙으면 false로 바꾸고 아래 fetch 분기를 채운다. */
const USE_MOCK = true

/** 목 응답에도 지연을 줘 로딩 스켈레톤이 실제로 보이게 한다. */
const MOCK_DELAY_MS = 400

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/** 종료된 방송 세션 목록. 최근 순으로 정렬되어 있다. */
export async function fetchEndedSessions(): Promise<EndedSessionSummary[]> {
  if (USE_MOCK) {
    await delay(MOCK_DELAY_MS)
    return MOCK_ENDED_SESSIONS
  }

  // const res = await fetch(`${BASE_URL}/broadcast-sessions?status=ENDED&page=0&size=20`)
  // const body = await res.json()
  // return body.data.content
  throw new Error('NOT_IMPLEMENTED')
}
