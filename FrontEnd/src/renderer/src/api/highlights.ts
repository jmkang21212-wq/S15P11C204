import { apiFetch } from './client'
import type { ReportHighlight } from '@renderer/types/report'

/**
 * SC-07 하이라이트 / 클립 데이터 소스 — `SessionReportController`
 * (`GET /broadcast-sessions/{sessionId}/highlights`, API 명세서 §11.4).
 *
 * 분석이 아직 끝나지 않았거나(PROCESSING) 실패했으면(FAILED) 서버가 409/500과
 * 에러 코드를 내려준다 — `ApiError.code`로 그대로 구분해 쓴다(ClipsPage 참고).
 * 여기서 별도 sentinel을 만들지 않는다.
 */
export async function fetchSessionHighlights(sessionId: number): Promise<ReportHighlight[]> {
  return apiFetch<ReportHighlight[]>(`/broadcast-sessions/${sessionId}/highlights`)
}
