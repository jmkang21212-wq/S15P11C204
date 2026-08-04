import { apiFetch } from './client'
import type { Page } from '@renderer/types/broadcastSession'
import type { CalendarEvent } from '@renderer/types/schedule'
import type {
  ConfirmPromiseInput,
  PromiseItem,
  PromiseStatus,
  SchedulePromiseInput,
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
 * 약속을 캘린더 방송 일정으로 전환한다. 일정 생성과 약속 상태 변경(SCHEDULED)이
 * 서버 한 트랜잭션이라, 프론트가 두 번 호출할 때 생기던 중간 상태가 없다.
 *
 * 응답은 만들어진 일정이다(`promiseId`가 채워져 온다). 전환된 약속은 `SCHEDULED`가 되어
 * `status=PENDING` 목록과 캘린더 약속 칩 양쪽에서 빠진다 — 일정이 대신 보이기 때문이다.
 *
 * 409: 약속이 PENDING·ON_HOLD가 아님 / 이미 활성 일정이 연결됨 / 플랫폼 계정 없음
 */
export async function schedulePromise(
  promiseId: number,
  input: SchedulePromiseInput
): Promise<CalendarEvent> {
  return apiFetch<CalendarEvent>(`${BASE}/${promiseId}/schedule`, {
    method: 'POST',
    body: JSON.stringify(input)
  })
}

/**
 * 확정된 약속의 상태·내용·기한을 부분 수정한다.
 * 캘린더 드래그 드롭은 `{ dueDate: 'yyyy-MM-dd' }` 하나만 보낸다.
 * 확정한 약속을 지우려면 `{ status: 'DISMISSED' }`를 보낸다 — 전용 dismiss API는 감지 상태만 받는다.
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
