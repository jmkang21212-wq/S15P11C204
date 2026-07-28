import { useEffect, useState } from 'react'
import { getCalendarEvents } from '@renderer/api/schedule'
import type { CalendarEvent } from '@renderer/types/schedule'

interface UseCalendarEventsResult {
  data: CalendarEvent[] | null
  isLoading: boolean
  error: Error | null
}

/**
 * 기간별 일정 로딩 훅.
 * 현재는 mock. 실제 연동 시 내부만 실제 API 호출로 교체하면 화면 코드는 그대로.
 */
export function useCalendarEvents(from: string, to: string): UseCalendarEventsResult {
  const [data, setData] = useState<CalendarEvent[] | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)

  useEffect(() => {
    let active = true
    setIsLoading(true)
    getCalendarEvents(from, to)
      .then((result) => {
        if (active) setData(result)
      })
      .catch((e) => {
        if (active) setError(e instanceof Error ? e : new Error('일정 로딩 실패'))
      })
      .finally(() => {
        if (active) setIsLoading(false)
      })
    return () => {
      active = false
    }
  }, [from, to])

  return { data, isLoading, error }
}
