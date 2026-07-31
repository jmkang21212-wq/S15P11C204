import { apiFetch } from './client'
import type { LiveSnapshot } from '@renderer/types/console'

/**
 * 실시간 콘솔 스냅샷 조회 (명세 10.6).
 *
 * 전달 방식은 WebSocket이 아니라 폴링이다(백엔드 2026-07-29 결정) — 호출부가
 * 2~3초 주기로 이 API를 호출해 콘솔의 시청자·여론·수집기 상태를 갱신한다.
 *
 * briefings는 백엔드 채팅 요약 파이프라인이 아직 없어 항상 빈 배열이다 — 위험 발언·약속
 * 브리핑은 AI 음성 서버에서 직접 받아(useAudioStream) 화면에서 합친다.
 * repeatedQuestions는 노출 중인 질문 전량(≤50)이 askers 내림차순으로 온다 — 상위 N 컷과
 * 타입 필터는 프론트(RepeatedQuestions)가 한다.
 */
export async function getLiveSnapshot(sessionId: number): Promise<LiveSnapshot> {
  return apiFetch<LiveSnapshot>(`/broadcast-sessions/${sessionId}/live-snapshot`)
}
