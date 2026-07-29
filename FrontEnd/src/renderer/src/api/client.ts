import { API_BASE_URL } from './config'
import { authStore } from '@renderer/auth/authStore'

/** API 오류 — Envelope의 error.code/message 또는 HTTP 상태를 담는다. */
export class ApiError extends Error {
  readonly status: number
  readonly code?: string
  constructor(message: string, status: number, code?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

interface ApiOptions extends RequestInit {
  /** false면 Authorization 헤더/401 자동 갱신을 하지 않는다 (refresh 자체 호출 등). */
  auth?: boolean
}

async function rawFetch(path: string, options: ApiOptions): Promise<Response> {
  const headers = new Headers(options.headers)
  if (options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const token = authStore.getToken()
  if (options.auth !== false && token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  // credentials: 'include' → Refresh Token(HttpOnly 쿠키) 전송
  return fetch(`${API_BASE_URL}${path}`, { ...options, headers, credentials: 'include' })
}

let refreshPromise: Promise<boolean> | null = null

/** Refresh Token 쿠키로 Access Token을 재발급받는다 (동시 호출은 1회로 합침). */
async function tryRefresh(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      try {
        const res = await fetch(`${API_BASE_URL}/auth/refresh`, {
          method: 'POST',
          credentials: 'include'
        })
        if (!res.ok) return false
        const json = await res.json()
        if (json?.success && json.data?.accessToken) {
          authStore.set({ accessToken: json.data.accessToken, streamer: json.data.streamer })
          return true
        }
        return false
      } catch {
        return false
      }
    })()
  }
  const result = await refreshPromise
  refreshPromise = null
  return result
}

/**
 * REST 호출 래퍼.
 * - 성공 Envelope `{ success, data }`의 data를 반환
 * - 401이면 refresh 후 1회 재시도
 * - 실패 시 ApiError throw
 */
export async function apiFetch<T>(path: string, options: ApiOptions = {}): Promise<T> {
  let res = await rawFetch(path, options)

  if (res.status === 401 && options.auth !== false) {
    const refreshed = await tryRefresh()
    if (refreshed) {
      res = await rawFetch(path, options)
    } else {
      authStore.clear()
    }
  }

  if (res.status === 204) return undefined as T

  const json = await res.json().catch(() => null)
  if (!res.ok || !json || json.success === false) {
    const code = json?.error?.code as string | undefined
    const message = (json?.error?.message as string | undefined) ?? `요청에 실패했어요 (${res.status})`
    throw new ApiError(message, res.status, code)
  }
  return json.data as T
}
