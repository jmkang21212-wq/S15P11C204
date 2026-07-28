import { MOCK_REPORTS } from '@renderer/mocks/sessionReport.mock'
import { isProcessing, type ReportHighlight } from '@renderer/types/report'

/**
 * SC-07 하이라이트 / 클립 목 데이터.
 *
 * 백엔드 `GET /broadcast-sessions/{sessionId}/highlights`(명세서 §11.4)가 미구현이라 화면 개발용으로 둔다.
 * 반환 필드가 SC-06 리포트 응답의 `highlights`와 동일하므로, 값이 어긋나지 않도록
 * 기존 리포트 목에서 그대로 끌어오고 여기에 "생성 중" 클립만 덧붙인다.
 */

const MINUTE_MS = 60_000

/** 리포트 목에서 하이라이트만 꺼낸다. 분석 중·실패 세션은 빈 배열이다. */
function highlightsOf(sessionId: number): ReportHighlight[] {
  const report = MOCK_REPORTS[sessionId]
  if (!report || isProcessing(report)) return []
  return report.highlights
}

/**
 * 아직 클립이 만들어지지 않은 하이라이트.
 * 명세서 §1.5대로 `clipUrl`·`thumbnailUrl`이 null이며, 화면에서 "생성 중"으로 표시된다.
 */
function pendingClip(
  highlightId: number,
  reportSegmentId: number,
  startMin: number,
  lengthSec: number
): ReportHighlight {
  return {
    highlightId,
    reportSegmentId,
    startMs: startMin * MINUTE_MS,
    endMs: startMin * MINUTE_MS + lengthSec * 1000,
    clipUrl: null,
    thumbnailUrl: null
  }
}

/** sessionId → 하이라이트 목록. `api/highlights.ts`에서만 참조한다. */
export const MOCK_HIGHLIGHTS: Record<number, ReportHighlight[]> = {
  // 완료 4건 + 생성 중 2건 — 두 상태를 한 화면에서 확인할 수 있다.
  9001: [...highlightsOf(9001), pendingClip(75, 403, 152, 47), pendingClip(76, 401, 78, 63)],
  // 분석 진행 중 세션 — 아직 하이라이트가 없다.
  9002: [],
  // AI 요약 실패(PARTIAL) 세션 — 하이라이트도 없어 빈 상태를 확인할 수 있다.
  9003: [],
  // 분석 실패 세션
  9004: [],
  9005: highlightsOf(9005),
  9006: [...highlightsOf(9006), pendingClip(77, 402, 96, 38)]
}
