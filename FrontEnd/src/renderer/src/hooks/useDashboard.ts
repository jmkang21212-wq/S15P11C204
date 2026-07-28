import { useEffect, useState } from 'react'
import { getDashboard } from '@renderer/api/dashboard'
import type { DashboardData } from '@renderer/types/dashboard'

interface UseDashboardResult {
  data: DashboardData | null
  isLoading: boolean
  error: Error | null
}

/**
 * 대시보드 데이터 로딩 훅.
 * 로딩/데이터/에러 상태를 반환해, 실제 API 연동 시에도 화면 코드가 그대로 유지된다.
 * (추후 react-query 등으로 내부만 교체 가능)
 */
export function useDashboard(): UseDashboardResult {
  const [data, setData] = useState<DashboardData | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)

  useEffect(() => {
    let active = true
    setIsLoading(true)
    getDashboard()
      .then((result) => {
        if (active) setData(result)
      })
      .catch((e) => {
        if (active) setError(e instanceof Error ? e : new Error('대시보드 로딩 실패'))
      })
      .finally(() => {
        if (active) setIsLoading(false)
      })
    return () => {
      active = false
    }
  }, [])

  return { data, isLoading, error }
}
