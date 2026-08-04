import { EmptyState } from '@renderer/components/common/StateView'
import { cn } from '@renderer/lib/utils'
import { formatDate, formatTime } from '@renderer/lib/format'
import { toDateKey } from './calendarGrid'
import type { CalendarEvent, CalendarPromiseItem } from '@renderer/types/schedule'

interface AgendaListViewProps {
  events: CalendarEvent[]
  /** 기한이 잡힌 진행 중 약속 — 읽기 전용 행으로만 표시한다 (확정/무시 API 연동 전). */
  promises: CalendarPromiseItem[]
  selectedEventId: number | null
  onSelectEvent: (event: CalendarEvent) => void
}

type AgendaItem =
  | { kind: 'event'; dateKey: string; sortAt: number; event: CalendarEvent }
  | { kind: 'promise'; dateKey: string; sortAt: number; promise: CalendarPromiseItem }

const PROMISE_STATUS_LABEL: Record<CalendarPromiseItem['status'], string> = {
  PENDING: '진행 중',
  ON_HOLD: '보류'
}

/**
 * 일정·약속을 날짜순으로 묶어 보여주는 목록 뷰. 그리드(CalendarMonth)와 같은 데이터를
 * 다르게 렌더링만 한다 — 별도 API 호출 없음. 일정 클릭은 그리드와 동일하게
 * `onSelectEvent`로 상세 패널을 연다. 약속은 확정/무시 API가 아직 없어 클릭 불가하다.
 */
export default function AgendaListView({
  events,
  promises,
  selectedEventId,
  onSelectEvent
}: AgendaListViewProps): React.JSX.Element {
  const items: AgendaItem[] = [
    ...events.map((event) => ({
      kind: 'event' as const,
      dateKey: toDateKey(new Date(event.startAt)),
      sortAt: new Date(event.startAt).getTime(),
      event
    })),
    ...promises.map((promise) => ({
      kind: 'promise' as const,
      dateKey: promise.dueDate,
      sortAt: new Date(`${promise.dueDate}T00:00:00`).getTime(),
      promise
    }))
  ].sort((a, b) => a.sortAt - b.sortAt)

  if (items.length === 0) {
    return (
      <div className="flex flex-1 items-center justify-center px-6 pb-6">
        <EmptyState title="이 달에 일정·약속이 없어요" />
      </div>
    )
  }

  // items가 이미 날짜순 정렬돼 있어 Map 삽입 순서를 그대로 날짜순 그룹 순서로 쓴다.
  const groups = new Map<string, AgendaItem[]>()
  for (const item of items) {
    const list = groups.get(item.dateKey) ?? []
    list.push(item)
    groups.set(item.dateKey, list)
  }

  return (
    <div className="flex flex-1 flex-col gap-5 overflow-y-auto px-6 pb-6">
      {[...groups.entries()].map(([dateKey, dayItems]) => (
        <div key={dateKey}>
          <p className="mb-2 text-xs font-semibold text-muted-foreground">
            {formatDate(`${dateKey}T00:00:00`)}
          </p>
          <ul className="flex flex-col gap-1.5">
            {dayItems.map((item) =>
              item.kind === 'event' ? (
                <li key={`event-${item.event.calendarEventId}`}>
                  <button
                    type="button"
                    onClick={() => onSelectEvent(item.event)}
                    className={cn(
                      'flex w-full items-center gap-2 rounded-lg border px-3 py-2 text-left text-sm',
                      item.event.calendarEventId === selectedEventId &&
                        'border-primary ring-1 ring-primary'
                    )}
                  >
                    <span
                      className={cn(
                        'rounded px-1.5 py-0.5 text-micro font-semibold',
                        item.event.eventType === 'DAY_OFF'
                          ? 'bg-muted text-muted-foreground'
                          : 'bg-accent text-accent-foreground'
                      )}
                    >
                      {item.event.eventType === 'DAY_OFF' ? '휴방' : '방송'}
                    </span>
                    {item.event.eventType === 'BROADCAST' && (
                      <span className="text-xs text-muted-foreground">
                        {formatTime(item.event.startAt)}
                      </span>
                    )}
                    <span className="flex-1 truncate">{item.event.title}</span>
                  </button>
                </li>
              ) : (
                <li
                  key={`promise-${item.promise.promiseId}`}
                  className="flex items-center gap-2 rounded-lg border border-dashed border-warning/40 bg-warning/5 px-3 py-2 text-sm text-warning"
                >
                  <span className="rounded bg-warning/10 px-1.5 py-0.5 text-micro font-semibold">
                    약속
                  </span>
                  <span className="flex-1 truncate">{item.promise.content}</span>
                  <span className="text-micro">{PROMISE_STATUS_LABEL[item.promise.status]}</span>
                </li>
              )
            )}
          </ul>
        </div>
      ))}
    </div>
  )
}
