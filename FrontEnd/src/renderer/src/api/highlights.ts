import { MOCK_HIGHLIGHTS } from '@renderer/mocks/highlights.mock'
import type { ReportHighlight } from '@renderer/types/report'

/**
 * SC-07 하이라이트 / 클립 데이터 소스.
 *
 * 백엔드 미구현 상태 — 현재는 목 데이터를 반환한다.
 * 실연동 시 이 파일만 고치면 되고, 페이지·컴포넌트는 손대지 않는다.
 *
 * TODO: GET /broadcast-sessions/{sessionId}/highlights 연동 (API 명세서 §11.4)
 *       `{ success, data }` 엔벨로프의 `data`를 반환하면 된다.
 */

/** 백엔드가 붙으면 false로 바꾸고 아래 fetch 분기를 채운다. */
const USE_MOCK = true

/** 목 응답에도 지연을 줘 로딩 스켈레톤이 실제로 보이게 한다. */
const MOCK_DELAY_MS = 400

/** 조회한 세션이 없을 때 던지는 에러 코드. */
export const SESSION_NOT_FOUND = 'SESSION_NOT_FOUND'

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export async function fetchSessionHighlights(sessionId: number): Promise<ReportHighlight[]> {
  if (USE_MOCK) {
    await delay(MOCK_DELAY_MS)
    const highlights = MOCK_HIGHLIGHTS[sessionId]
    // 하이라이트 0건과 "세션 자체가 없음"은 다른 상태다 — 전자는 빈 상태, 후자는 에러.
    if (!highlights) throw new Error(SESSION_NOT_FOUND)
    return highlights
  }

  // const res = await fetch(`${BASE_URL}/broadcast-sessions/${sessionId}/highlights`)
  // if (res.status === 404) throw new Error(SESSION_NOT_FOUND)
  // const body = await res.json()
  // return body.data
  throw new Error('NOT_IMPLEMENTED')
}
