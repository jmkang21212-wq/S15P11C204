/**
 * 월 캘린더 그리드 날짜 계산 — 그리드 렌더링(CalendarMonth)·조회 범위(SchedulePage)·
 * 목록 뷰 그룹핑(AgendaListView)이 전부 이 기준을 공유해야 "그리드에는 보이는데
 * 데이터는 없는" 칸이 생기지 않는다.
 */

/** 월 캘린더가 그리는 6주(42일) 그리드의 날짜 배열 (일요일 시작). */
export function getMonthGridDays(year: number, month: number): Date[] {
  const startWeekday = new Date(year, month, 1).getDay()
  const gridStart = new Date(year, month, 1 - startWeekday)
  return Array.from({ length: 42 }, (_, i) => {
    const d = new Date(gridStart)
    d.setDate(gridStart.getDate() + i)
    return d
  })
}

/** 로컬 날짜를 BE LocalDate 문자열과 같은 형식(yyyy-MM-dd)으로. 약속 dueDate 매칭용. */
export function toDateKey(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}
