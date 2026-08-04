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

/**
 * 일정을 다른 날짜로 옮긴 시작·종료 시각. **시각과 기간은 그대로 두고 날짜만 바꾼다** —
 * 드래그는 "언제 하는지"를 옮기는 동작이고 몇 시에 몇 시간 하는지를 바꾸는 게 아니다.
 *
 * 시작 시각에 날짜 차이를 더하는 방식이라 여러 날에 걸친 일정(종일 휴방 등)도 기간이 유지되고,
 * `end_at > start_at` 제약이 자동으로 지켜진다. 두 값을 따로 계산하면 그 보장이 깨진다.
 */
export function shiftEventToDate(
  startAt: string,
  endAt: string,
  targetDateKey: string
): { startAt: string; endAt: string } {
  const start = new Date(startAt)
  const [year, month, day] = targetDateKey.split('-').map(Number)

  const shifted = new Date(start)
  shifted.setFullYear(year, month - 1, day)
  const deltaMs = shifted.getTime() - start.getTime()

  return {
    startAt: shifted.toISOString(),
    endAt: new Date(new Date(endAt).getTime() + deltaMs).toISOString()
  }
}
