import { apiFetch } from './client'
import type { ConfirmPromiseInput, PromiseItem } from '@renderer/types/promise'

/**
 * 약속 관리 API — `/api/v1/promises` (PromiseController, 명세 10.8).
 * AI가 감지해 DETECTED로 저장한 약속을 사람이 확정·무시하는 몫만 다룬다.
 * 감지 자체(AI → BE 저장)는 이 화면이 관여하지 않는다.
 */

const BASE = '/promises'

/** 감지된 약속을 확정한다. content·dueDate는 둘 다 생략 가능 — 안 보내면 AI 문구·기한 미정으로 확정된다. */
export async function confirmPromise(
  promiseId: number,
  input: ConfirmPromiseInput = {}
): Promise<PromiseItem> {
  return apiFetch<PromiseItem>(`${BASE}/${promiseId}/confirm`, {
    method: 'POST',
    body: JSON.stringify(input)
  })
}

/** 감지된 약속을 무시한다. 204 No Content — apiFetch가 undefined로 돌려준다. */
export async function dismissPromise(promiseId: number): Promise<void> {
  await apiFetch<void>(`${BASE}/${promiseId}/dismiss`, { method: 'POST' })
}
