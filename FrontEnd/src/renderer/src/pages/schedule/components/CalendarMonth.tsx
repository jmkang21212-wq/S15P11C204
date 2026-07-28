import { Button } from '@renderer/components/ui/button'
import { cn } from '@renderer/lib/utils'
import { formatTime } from '@renderer/lib/format'
import type { CalendarEvent } from '@renderer/types/schedule'

interface CalendarMonthProps {
  year: number
  month: number // 0-indexed
  events: CalendarEvent[]
  selectedEventId: number | null
  onPrevMonth: () => void
  onNextMonth: () => void
  onSelectEvent: (event: CalendarEvent) => void
  onAddEvent: () => void
}

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

function dateKey(d: Date): string {
  return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`
}

export default function CalendarMonth({
  year,
  month,
  events,
  selectedEventId,
  onPrevMonth,
  onNextMonth,
  onSelectEvent,
  onAddEvent,
}: CalendarMonthProps): React.JSX.Element {
  // 일정을 날짜별로 그룹화 (로컬 날짜 기준)
  const eventsByDay = new Map<string, CalendarEvent[]>()
  for (const e of events) {
    const key = dateKey(new Date(e.startAt))
    const list = eventsByDay.get(key) ?? []
    list.push(e)
    eventsByDay.set(key, list)
  }

  // 6주 × 7일 그리드 생성
  const startWeekday = new Date(year, month, 1).getDay()
  const gridStart = new Date(year, month, 1 - startWeekday)
  const cells = Array.from({ length: 42 }, (_, i) => {
    const d = new Date(gridStart)
    d.setDate(gridStart.getDate() + i)
    return d
  })

  return (
    <div className="flex flex-1 flex-col overflow-hidden p-6">
      {/* 헤더 */}
      <div className="mb-4 flex items-center gap-3">
        <div className="flex rounded-lg bg-muted p-0.5">
          <span className="rounded-md bg-card px-3.5 py-1.5 text-xs font-semibold shadow-sm">월</span>
          <span className="px-3.5 py-1.5 text-xs font-semibold text-muted-foreground">주</span>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="icon-sm" onClick={onPrevMonth} aria-label="이전 달">
            ◀
          </Button>
          <span className="text-base font-bold">
            {year}년 {month + 1}월
          </span>
          <Button variant="outline" size="icon-sm" onClick={onNextMonth} aria-label="다음 달">
            ▶
          </Button>
        </div>
        <span className="rounded-lg border border-dashed px-3 py-1.5 text-xs text-muted-foreground">
          추천 시간대 보기 (준비 중)
        </span>
        <Button className="ml-auto" size="sm" onClick={onAddEvent}>
          + 일정 추가
        </Button>
      </div>

      {/* 요일 헤더 */}
      <div className="grid grid-cols-7 border-b">
        {WEEKDAYS.map((w, i) => (
          <div
            key={w}
            className={cn(
              'py-2 text-center text-xs font-semibold text-muted-foreground',
              i === 0 && 'text-destructive',
              i === 6 && 'text-cyan-600',
            )}
          >
            {w}
          </div>
        ))}
      </div>

      {/* 날짜 그리드 */}
      <div className="grid flex-1 grid-cols-7 grid-rows-6 overflow-hidden">
        {cells.map((d, i) => {
          const inMonth = d.getMonth() === month
          const dayEvents = eventsByDay.get(dateKey(d)) ?? []
          return (
            <div
              key={i}
              className={cn(
                'min-h-0 overflow-hidden border-r border-b p-1.5',
                !inMonth && 'bg-muted/30',
              )}
            >
              <span
                className={cn(
                  'text-xs',
                  inMonth ? 'text-foreground' : 'text-muted-foreground/50',
                  d.getDay() === 0 && inMonth && 'text-destructive',
                )}
              >
                {d.getDate()}
              </span>
              <div className="mt-1 flex flex-col gap-1">
                {dayEvents.map((e) => (
                  <button
                    key={e.calendarEventId}
                    type="button"
                    onClick={() => onSelectEvent(e)}
                    className={cn(
                      'truncate rounded px-1.5 py-0.5 text-left text-[10.5px] leading-tight',
                      e.eventType === 'DAY_OFF'
                        ? 'bg-muted text-muted-foreground'
                        : 'bg-accent text-accent-foreground',
                      e.calendarEventId === selectedEventId && 'ring-2 ring-primary',
                    )}
                  >
                    {e.eventType === 'BROADCAST' && (
                      <b className="font-mono">{formatTime(e.startAt)} </b>
                    )}
                    {e.title}
                  </button>
                ))}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
