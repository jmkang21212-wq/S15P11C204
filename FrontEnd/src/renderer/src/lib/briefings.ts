import type { Briefing } from '@renderer/types/console'

/** AI 음성 서버가 방송 중 WebSocket으로 직접 밀어주는 유형 (useAudioStream). */
const REALTIME_TYPES: ReadonlySet<Briefing['briefingType']> = new Set(['RISK', 'PROMISE'])

/**
 * 실시간(WS) 브리핑과 스냅샷(DB) 브리핑을 중복 없이 합친다.
 *
 * 같은 위험 발언·약속이 두 경로로 도착하지만 둘은 공유 키가 없다 — id(프론트 음수
 * 발급 vs DB id), 문구(프론트 생성 vs AI briefingMessage), 시각(발화 종료 vs DB 저장)이
 * 전부 다르다. 그래서 건별 중복 제거 대신 시각으로 가른다: RISK·PROMISE는 컷오버
 * 이전 것만 스냅샷에서 히스토리로 받고(새로고침·재입장 복구), 이후는 WS가 전담한다.
 * CHAT_SUMMARY처럼 WS로 오지 않는 유형은 항상 스냅샷에서 받는다.
 *
 * 컷오버가 안전한 근거: RISK·PROMISE는 이 콘솔이 보내는 마이크 오디오에서만 생기므로,
 * WS가 붙기 전(=오디오가 없던) 구간의 스냅샷 브리핑은 전부 이전 콘솔 인스턴스 것이다.
 *
 * @param cutoff 콘솔 진입(WS 수신 시작) 시각, ISO 8601. null이면 RISK·PROMISE를
 *        스냅샷에서 아예 받지 않는다 — 미니 창처럼 WS 몫을 main의 IPC 릴레이로만
 *        받는 화면용(릴레이 버퍼가 히스토리를 겸한다).
 */
export function mergeBriefings(
  realtime: Briefing[],
  snapshot: Briefing[],
  cutoff: string | null
): Briefing[] {
  // ISO 문자열 사전순 비교는 소수점 초 자릿수가 다르면(…00Z vs …00.123Z) 어긋난다.
  const cutoffMs = cutoff === null ? null : Date.parse(cutoff)
  const history = snapshot.filter((briefing) =>
    REALTIME_TYPES.has(briefing.briefingType)
      ? cutoffMs !== null && Date.parse(briefing.createdAt) < cutoffMs
      : true
  )
  return [...realtime, ...history].sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))
}
