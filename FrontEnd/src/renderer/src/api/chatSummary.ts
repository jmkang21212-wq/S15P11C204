import { apiFetch } from './client'

/**
 * 수동 여론 요약 생성 결과 (계약 §2의 MANUAL).
 *
 * 쿨다운·생성 중·채팅 부족은 오류가 아니라 200의 status로 내려온다 —
 * 프론트가 버튼을 비활성화하고 카운트다운·안내를 그릴 수 있게 한 설계다.
 */
export type ChatSummaryStatus = 'CREATED' | 'COOLDOWN' | 'IN_FLIGHT' | 'NOT_ENOUGH_CHATS'

export interface ChatSummaryCreateResult {
  status: ChatSummaryStatus
  /** CREATED일 때만 — 생성된 요약문 (브리핑으로도 저장돼 live-snapshot에 실려 온다) */
  summary: string | null
  generatedAt: string | null
  /** 다음 요약이 가능해지는 시각 */
  nextAvailableAt: string | null
  /** COOLDOWN·IN_FLIGHT일 때 몇 초 뒤에 다시 시도할 수 있는지 */
  retryAfterSec: number | null
  /** NOT_ENOUGH_CHATS일 때 현재 채팅 수 */
  chatCount: number | null
  /** NOT_ENOUGH_CHATS일 때 필요한 최소 채팅 수 */
  required: number | null
}

/**
 * 여론 요약을 즉시 생성한다 — `POST /broadcast-sessions/{id}/chat-summary`.
 *
 * 생성된 요약은 CHAT_SUMMARY 브리핑으로 저장되므로, 화면에는 이 응답이 아니라
 * 다음 live-snapshot 폴링을 통해 브리핑 피드에 나타난다.
 */
export async function createChatSummary(sessionId: number): Promise<ChatSummaryCreateResult> {
  return apiFetch<ChatSummaryCreateResult>(`/broadcast-sessions/${sessionId}/chat-summary`, {
    method: 'POST'
  })
}
