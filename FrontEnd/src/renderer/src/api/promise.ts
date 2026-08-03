import { apiFetch } from './client'
import type { Page } from '@renderer/types/broadcastSession'
import type {
  ConfirmPromiseInput,
  PromiseItem,
  PromiseStatus,
  UpdatePromiseInput
} from '@renderer/types/promise'

/**
 * 약속 관리 API — `/api/v1/promises` (PromiseController, 명세 10.8).
 * AI가 감지해 DETECTED로 저장한 약속을 사람이 확정·무시하는 몫만 다룬다.
 * 감지 자체(AI → BE 저장)는 이 화면이 관여하지 않는다.
 */

const BASE = '/promises'

/** BE가 허용하는 page size 상한 (`@Max(100)`). */
export const PROMISE_PAGE_MAX_SIZE = 100

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

/**
 * 상태별 약속 목록. `status`를 생략하면 전체다.
 *
 * 캘린더의 "기한 미정" 목록이 이 API를 쓴다 — 캘린더 조회(`GET /calendar-events`)는
 * `due_date IS NOT NULL`로 걸러내므로 기한 없는 약속을 그쪽에서는 절대 받을 수 없다.
 */
export async function listPromises(
  status?: PromiseStatus,
  page = 0,
  size = PROMISE_PAGE_MAX_SIZE
): Promise<Page<PromiseItem>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (status !== undefined) params.set('status', status)
  return apiFetch<Page<PromiseItem>>(`${BASE}?${params}`)
}

/**
 * 확정된 약속의 상태·내용·기한을 부분 수정한다.
 * 캘린더 드래그 드롭은 `{ dueDate: 'yyyy-MM-dd' }` 하나만 보낸다.
 */
export async function updatePromise(
  promiseId: number,
  input: UpdatePromiseInput
): Promise<PromiseItem> {
  return apiFetch<PromiseItem>(`${BASE}/${promiseId}`, {
    method: 'PATCH',
    body: JSON.stringify(input)
  })
}
