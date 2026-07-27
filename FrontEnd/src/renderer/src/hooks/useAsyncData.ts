import { useCallback, useEffect, useState } from 'react'

/**
 * 비동기 조회 한 건을 로딩/에러/재조회 상태와 함께 다루는 최소 훅.
 *
 * 아직 데이터 페칭 라이브러리를 도입하지 않아 화면마다 useEffect를 반복하지 않도록 공용화했다.
 * 캐싱·중복 제거는 하지 않는다 — 필요해지면 TanStack Query로 갈아탄다.
 */
export interface AsyncData<T> {
  data: T | null
  loading: boolean
  error: Error | null
  refetch: () => void
}

/** 완료된 조회의 결과 + 그 조회를 식별하는 값. */
interface Settled<T> {
  loader: unknown
  nonce: number
  data: T | null
  error: Error | null
}

/**
 * @param loader 매 조회마다 실행할 함수. 참조가 바뀌면 다시 조회하므로 useCallback으로 감싸 넘긴다.
 */
export function useAsyncData<T>(loader: () => Promise<T>): AsyncData<T> {
  const [settled, setSettled] = useState<Settled<T> | null>(null)
  const [nonce, setNonce] = useState(0)

  /**
   * 로딩은 별도 state로 두지 않고 파생시킨다.
   * 아직 완료된 결과가 없거나, 완료된 결과가 지금 요청(loader·nonce)의 것이 아니면 로딩 중이다.
   * effect 안에서 setState를 동기 호출하지 않게 되어 불필요한 연쇄 렌더가 없다.
   */
  const loading = settled === null || settled.loader !== loader || settled.nonce !== nonce

  useEffect(() => {
    // 언마운트되거나 새 요청이 시작되면 이전 응답이 상태를 덮어쓰지 못하게 한다.
    let cancelled = false

    loader().then(
      (result) => {
        if (!cancelled) setSettled({ loader, nonce, data: result, error: null })
      },
      (e: unknown) => {
        if (!cancelled) {
          setSettled({
            loader,
            nonce,
            data: null,
            error: e instanceof Error ? e : new Error(String(e))
          })
        }
      }
    )

    return () => {
      cancelled = true
    }
  }, [loader, nonce])

  const refetch = useCallback(() => setNonce((n) => n + 1), [])

  return {
    data: loading ? null : (settled?.data ?? null),
    loading,
    error: loading ? null : (settled?.error ?? null),
    refetch
  }
}
