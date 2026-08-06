import { useState } from 'react'
import { cn } from '@renderer/lib/utils'
import { formatTime } from '@renderer/lib/format'
import { getMonthGridDays, toDateKey } from './calendarGrid'
import { isScheduleDrag, readScheduleDragData, setScheduleDragData } from './scheduleDnd'
import type { ScheduleDragPayload } from './scheduleDnd'
import type { CalendarEvent, CalendarPromiseItem } from '@renderer/types/schedule'

interface CalendarMonthProps {
  year: number
  month: number // 0-indexed
  events: CalendarEvent[]
  /** 기한이 잡힌 진행 중 약속. 칩을 다른 날짜로 끌어 기한을 옮길 수 있다. */
  promises: CalendarPromiseItem[]
  selectedEventId: number | null
  onSelectEvent: (event: CalendarEvent) => void
  /**
   * 약속·일정을 날짜 칸에 떨어뜨렸을 때. 종류와 출처에 따라 동작이 갈린다 —
   * 미정 약속은 일정 전환, 약속 칩은 기한 이동, 일정은 날짜 이동이다.
   */
  onDropItem: (payload: ScheduleDragPayload, dateKey: string) => void
  /** 처리 중인 약속. 중복 드롭을 막고 칩을 흐리게 표시한다. */
  movingPromiseId: number | null
  /** 처리 중인 일정. 같은 이유로 잠근다. */
  movingEventId: number | null
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
  onSelectEvent,
  onDropItem,
  movingPromiseId,
  movingEventId
}: CalendarMonthProps): React.JSX.Element {
  const cells = getMonthGridDays(year, month)
  // 어느 칸 위에 있는지 표시용. dragleave가 자식 요소 경계마다 튀므로 칸 단위 키로 관리한다.
  const [dragOverKey, setDragOverKey] = useState<string | null>(null)

  const handleDrop = (event: React.DragEvent, dateKey: string): void => {
    setDragOverKey(null)
    if (!isScheduleDrag(event.dataTransfer)) return
    event.preventDefault()
    const payload = readScheduleDragData(event.dataTransfer)
    if (payload !== null) onDropItem(payload, dateKey)
  }

  /** preventDefault를 해야 브라우저가 드롭을 허용한다. 캘린더 항목 드래그가 아니면 받지 않는다. */
  const handleDragOver = (event: React.DragEvent, dateKey: string): void => {
    if (!isScheduleDrag(event.dataTransfer)) return
    event.preventDefault()
    event.dataTransfer.dropEffect = 'move'
    if (dragOverKey !== dateKey) setDragOverKey(dateKey)
  }

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

      {/*
        날짜 그리드 — 6주 고정이라 셀 높이가 창 높이에 나뉜다. 하루에 일정이 여러 건이면
        셀 높이를 넘기므로, 셀마다 목록을 독립적으로 스크롤해 가려지는 항목이 없게 한다.
        행 높이를 내용에 맞춰 늘리는 방식은 쓰지 않았다 — 한 날짜 때문에 그 주 전체가
        두꺼워져 월 그리드의 균일함이 깨진다.
      */}
      <div className="grid min-h-0 flex-1 grid-cols-7 grid-rows-6">
        {cells.map((d, i) => {
          const inMonth = d.getMonth() === month
          const dayEvents = events.filter((e) => overlapsDay(e, d))
          const dayKey = toDateKey(d)
          const dayPromises = promises.filter((p) => p.dueDate === dayKey)
          return (
            <div
              key={i}
              onDragOver={(event) => handleDragOver(event, dayKey)}
              onDragLeave={() => setDragOverKey((key) => (key === dayKey ? null : key))}
              onDrop={(event) => handleDrop(event, dayKey)}
              className={cn(
                'flex min-h-0 flex-col overflow-hidden border-r border-b p-1.5',
                !inMonth && 'bg-muted/30',
                dragOverKey === dayKey && 'bg-primary/10 ring-2 ring-primary ring-inset'
              )}
            >
              {/* 날짜 숫자는 스크롤과 함께 밀리지 않게 셀 상단에 고정한다 */}
              <span
                className={cn(
                  'shrink-0 text-xs',
                  inMonth ? 'text-foreground' : 'text-muted-foreground/50',
                  d.getDay() === 0 && inMonth && 'text-destructive'
                )}
              >
                {d.getDate()}
              </span>
              {/*
                넘치는 만큼만 스크롤한다. 스크롤바는 감춘다 — 셀 폭이 좁아 스크롤바가
                차지하는 자리만큼 칩 내용이 밀려 읽기 어려워지기 때문이다. 휠·트랙패드·
                드래그 스크롤은 그대로 동작하고, 넘칠 때 마지막 칩이 반쯤 잘려 보이는 것이
                "더 있다"는 신호가 된다.
              */}
              <div className="mt-1 flex min-h-0 flex-1 flex-col gap-1 overflow-y-auto scrollbar-none [&::-webkit-scrollbar]:hidden">
                {/*
                  일정 칩 — 다른 날짜로 끌면 시각·기간은 그대로 두고 날짜만 옮겨진다.
                  이미 끝난 일정(COMPLETED)은 옮기지 않는다. 방송이 실제로 있었던 날을
                  바꾸는 셈이라 기록을 왜곡한다.
                */}
                {dayEvents.map((e) => {
                  const movable = e.status === 'SCHEDULED' && movingEventId !== e.calendarEventId
                  return (
                    <button
                      key={e.calendarEventId}
                      type="button"
                      draggable={movable}
                      onDragStart={(event) =>
                        setScheduleDragData(event.dataTransfer, {
                          kind: 'event',
                          id: e.calendarEventId
                        })
                      }
                      onClick={() => onSelectEvent(e)}
                      title={movable ? `${e.title} — 다른 날짜로 끌어 일정 변경` : e.title}
                      className={cn(
                        'flex w-full shrink-0 flex-col rounded border-b-2 px-1.5 py-1 text-left text-caption leading-tight',
                        e.eventType === 'DAY_OFF'
                          ? 'bg-muted text-muted-foreground'
                          : 'bg-accent text-accent-foreground',
                        e.calendarEventId === selectedEventId
                          ? 'border-primary'
                          : 'border-transparent',
                        movable && 'cursor-grab active:cursor-grabbing',
                        movingEventId === e.calendarEventId && 'opacity-50'
                      )}
                    >
                      {e.eventType === 'BROADCAST' && (
                        <span className="font-bold opacity-70">{formatTime(e.startAt)}</span>
                      )}
                      <span className="line-clamp-2 wrap-break-word">{e.title}</span>
                    </button>
                  )
                })}
                {/* 약속 칩 — 다른 날짜로 끌면 기한이 옮겨진다(잘못 떨어뜨려도 되돌릴 수 있다). */}
                {dayPromises.map((p) => {
                  const moving = movingPromiseId === p.promiseId
                  return (
                    <div
                      key={p.promiseId}
                      draggable={!moving}
                      onDragStart={(event) =>
                        setScheduleDragData(event.dataTransfer, {
                          kind: 'promise',
                          id: p.promiseId,
                          source: 'calendar'
                        })
                      }
                      title={`${p.content} — 다른 날짜로 끌어 기한 변경`}
                      className={cn(
                        'shrink-0 rounded border border-dashed border-warning/40 bg-warning/5 px-1.5 py-1 text-left text-caption leading-tight text-warning',
                        moving ? 'opacity-50' : 'cursor-grab active:cursor-grabbing'
                      )}
                    >
                      <span className="line-clamp-2 wrap-break-word">🔖 {p.content}</span>
                    </div>
                  )
                })}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
