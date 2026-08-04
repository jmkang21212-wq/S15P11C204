import { apiFetch } from './client'
import type { LiveSnapshot } from '@renderer/types/console'

/**
 * 실시간 콘솔 스냅샷 조회 (명세 10.6).
 *
 * 전달 방식은 WebSocket이 아니라 폴링이다(백엔드 2026-07-29 결정) — 호출부가
 * 2~3초 주기로 이 API를 호출해 콘솔의 시청자·여론·수집기 상태를 갱신한다.
 *
 * briefings는 live_briefings의 최근 20건(최신순)이다 — RISK·PROMISE(AI 분석 저장분)와
 * CHAT_SUMMARY(여론 요약)가 실린다. 방송 중 RISK·PROMISE는 AI 음성 서버가 WS로 직접
 * 밀어주므로(useAudioStream) 같은 사건이 두 경로로 온다 — 병합 규칙은
 * lib/briefings.ts의 mergeBriefings가 정한다(컷오버 이전 것만 스냅샷에서 히스토리로).
 * repeatedQuestions는 노출 중인 질문 전량(≤50)이 askers 내림차순으로 온다 — 상위 N 컷과
 * 타입 필터는 프론트(RepeatedQuestions)가 한다.
 */
export async function getLiveSnapshot(sessionId: number): Promise<LiveSnapshot> {
  return apiFetch<LiveSnapshot>(`/broadcast-sessions/${sessionId}/live-snapshot`)
}
