import { cn } from '@renderer/lib/utils'
import { formatTime } from '@renderer/lib/format'
import { getMonthGridDays, toDateKey } from './calendarGrid'
import type { CalendarEvent, CalendarPromiseItem } from '@renderer/types/schedule'

interface CalendarMonthProps {
  year: number
  month: number // 0-indexed
  events: CalendarEvent[]
  /** 기한이 잡힌 진행 중 약속 — 읽기 전용 칩으로만 표시한다 (확정/무시 API 연동 전). */
  promises: CalendarPromiseItem[]
  selectedEventId: number | null
  onSelectEvent: (event: CalendarEvent) => void
}

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

/**
 * 셀 날짜(자정~다음 자정)와 일정 구간이 겹치는지.
 * BE `findRange`가 포함이 아니라 겹침으로 조회하므로(여러 날짜짜리 DAY_OFF가 경계를
 * 걸칠 수 있다), 화면도 시작일 하나에만 꽂지 않고 걸치는 모든 날짜 셀에 그린다.
 */
function overlapsDay(event: CalendarEvent, day: Date): boolean {
  const dayStart = new Date(day.getFullYear(), day.getMonth(), day.getDate())
  const dayEnd = new Date(dayStart)
  dayEnd.setDate(dayEnd.getDate() + 1)
  return new Date(event.startAt) < dayEnd && new Date(event.endAt) > dayStart
}

export default function CalendarMonth({
  year,
  month,
  events,
  promises,
  selectedEventId,
  onSelectEvent
}: CalendarMonthProps): React.JSX.Element {
  const cells = getMonthGridDays(year, month)

  return (
    <div className="flex flex-1 flex-col overflow-hidden px-6 pb-6">
      {/* 요일 헤더 */}
      <div className="grid grid-cols-7 border-b">
        {WEEKDAYS.map((w, i) => (
          <div
            key={w}
            className={cn(
              'py-2 text-center text-xs font-semibold text-muted-foreground',
              i === 0 && 'text-destructive',
              i === 6 && 'text-cyan-600'
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
          const dayEvents = events.filter((e) => overlapsDay(e, d))
          const dayKey = toDateKey(d)
          const dayPromises = promises.filter((p) => p.dueDate === dayKey)
          return (
            <div
              key={i}
              className={cn(
                'min-h-0 overflow-hidden border-r border-b p-1.5',
                !inMonth && 'bg-muted/30'
              )}
            >
              <span
                className={cn(
                  'text-xs',
                  inMonth ? 'text-foreground' : 'text-muted-foreground/50',
                  d.getDay() === 0 && inMonth && 'text-destructive'
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
                    title={e.title}
                    className={cn(
                      'flex w-full flex-col rounded px-1.5 py-1 text-left text-caption leading-tight',
                      e.eventType === 'DAY_OFF'
                        ? 'bg-muted text-muted-foreground'
                        : 'bg-accent text-accent-foreground',
                      e.calendarEventId === selectedEventId && 'ring-2 ring-primary'
                    )}
                  >
                    {e.eventType === 'BROADCAST' && (
                      <span className="font-bold opacity-70">{formatTime(e.startAt)}</span>
                    )}
                    <span className="line-clamp-2 break-words">{e.title}</span>
                  </button>
                ))}
                {/* 약속 — 확정/무시 API가 아직 없어 클릭 불가한 읽기 전용 칩으로만 얹는다. */}
                {dayPromises.map((p) => (
                  <div
                    key={p.promiseId}
                    title={p.content}
                    className="rounded border border-dashed border-warning/40 bg-warning/5 px-1.5 py-1 text-left text-caption leading-tight text-warning"
                  >
                    <span className="line-clamp-2 break-words">🔖 {p.content}</span>
                  </div>
                ))}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
