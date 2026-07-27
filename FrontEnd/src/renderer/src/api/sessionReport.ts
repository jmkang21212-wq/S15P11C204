import { MOCK_ENDED_SESSIONS, MOCK_REPORTS } from '@renderer/mocks/sessionReport.mock'
import type { EndedSessionSummary, SessionReportResult } from '@renderer/types/report'

/**
 * SC-06 세션 리포트 데이터 소스.
 *
 * 백엔드 미구현 상태 — 현재는 목 데이터를 반환한다.
 * 실연동 시 이 파일만 고치면 되고, 페이지·컴포넌트는 손대지 않는다.
 *
 * TODO: GET /broadcast-sessions/{sessionId}/report 연동  (API 명세서 §11.1)
 * TODO: GET /broadcast-sessions?status=ENDED 연동         (API 명세서 §10.1)
 *       두 엔드포인트 모두 `{ success, data }` 엔벨로프이므로 `data`만 벗겨 반환하면 된다.
 *       리포트 조회는 status가 PROCESSING이면 200이 아니라 202로 내려온다.
 */

/** 백엔드가 붙으면 false로 바꾸고 아래 fetch 분기를 채운다. */
const USE_MOCK = true

/** 목 응답에도 지연을 줘 로딩 스켈레톤이 실제로 보이게 한다. */
const MOCK_DELAY_MS = 400

/** 조회한 세션의 리포트가 없을 때 던지는 에러 코드. */
export const REPORT_NOT_FOUND = 'REPORT_NOT_FOUND'

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

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

export async function fetchSessionReport(sessionId: number): Promise<SessionReportResult> {
  if (USE_MOCK) {
    await delay(MOCK_DELAY_MS)
    const report = MOCK_REPORTS[sessionId]
    if (!report) throw new Error(REPORT_NOT_FOUND)
    return report
  }

  // const res = await fetch(`${BASE_URL}/broadcast-sessions/${sessionId}/report`)
  // if (res.status === 404) throw new Error(REPORT_NOT_FOUND)
  // const body = await res.json()
  // return body.data   // 202면 SessionReportProcessing, 200이면 SessionReport
  throw new Error('NOT_IMPLEMENTED')
}
