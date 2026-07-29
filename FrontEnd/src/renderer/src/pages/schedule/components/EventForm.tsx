import { useState } from 'react'
import { Button } from '@renderer/components/ui/button'
import { Input } from '@renderer/components/ui/input'
import { Label } from '@renderer/components/ui/label'
import { cn } from '@renderer/lib/utils'
import type { CalendarEvent, CalendarEventType } from '@renderer/types/schedule'

interface EventFormProps {
  event: CalendarEvent | null
  onSave: (event: CalendarEvent) => void
  onClose: () => void
  onDelete: (event: CalendarEvent) => void
}

function toTimeInput(iso: string): string {
  const d = new Date(iso)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function withTime(iso: string, time: string): string {
  const d = new Date(iso)
  const [h, m] = time.split(':').map(Number)
  d.setHours(h, m, 0, 0)
  return d.toISOString()
}

/**
 * 선택된 일정이 바뀌면 폼이 초기화돼야 하므로, 부모(SchedulePage)가
 * `key={event?.calendarEventId ?? 'empty'}`로 이 컴포넌트를 매번 새로
 * 마운트한다. 그래서 아래 초기값은 effect 없이 한 번만 평가되면 된다.
 */
export default function EventForm({
  event,
  onSave,
  onClose,
  onDelete
}: EventFormProps): React.JSX.Element {
  const [eventType, setEventType] = useState<CalendarEventType>(event?.eventType ?? 'BROADCAST')
  const [title, setTitle] = useState(event?.title ?? '')
  const [startTime, setStartTime] = useState(event ? toTimeInput(event.startAt) : '21:00')
  const [endTime, setEndTime] = useState(event ? toTimeInput(event.endAt) : '24:00')
  const [memo, setMemo] = useState(event?.memo ?? '')

  if (!event) {
    return (
      <div className="flex h-full items-center justify-center px-6 text-center text-sm text-muted-foreground">
        날짜의 일정을 선택하거나
        <br />
        “+ 일정 추가”를 눌러 새 일정을 만드세요.
      </div>
    )
  }

  const handleSave = (): void => {
    onSave({
      ...event,
      eventType,
      title,
      memo: memo || null,
      startAt: withTime(event.startAt, startTime),
      endAt: withTime(event.endAt, endTime)
    })
  }

  const isNew = event.calendarEventId < 0

  return (
    <div className="flex flex-col gap-4 p-5">
      <div className="flex items-center justify-between">
        <span className="text-sm font-bold">일정 {isNew ? '추가' : '수정'}</span>
        <button
          type="button"
          onClick={onClose}
          aria-label="닫기"
          className="text-muted-foreground hover:text-foreground"
        >
          ✕
        </button>
      </div>

      {/* 유형 토글 */}
      <div className="flex gap-2">
        {(['BROADCAST', 'DAY_OFF'] as const).map((t) => (
          <button
            key={t}
            type="button"
            onClick={() => setEventType(t)}
            className={cn(
              'flex-1 rounded-lg border py-2 text-xs font-semibold',
              eventType === t
                ? 'border-primary bg-primary text-primary-foreground'
                : 'text-muted-foreground'
            )}
          >
            {t === 'BROADCAST' ? '방송' : '휴방'}
          </button>
        ))}
      </div>

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="title">제목</Label>
        <Input id="title" value={title} onChange={(e) => setTitle(e.target.value)} />
      </div>

      {eventType === 'BROADCAST' && (
        <div className="flex flex-col gap-1.5">
          <Label>시간</Label>
          <div className="flex items-center gap-2">
            <Input type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} />
            <span className="text-muted-foreground">–</span>
            <Input type="time" value={endTime} onChange={(e) => setEndTime(e.target.value)} />
          </div>
        </div>
      )}

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="memo">메모</Label>
        <textarea
          id="memo"
          value={memo}
          onChange={(e) => setMemo(e.target.value)}
          rows={3}
          className="rounded-md border px-3 py-2 text-sm"
        />
      </div>

      <div className="flex gap-2">
        <Button className="flex-1" onClick={handleSave}>
          저장
        </Button>
        {!isNew && (
          // TODO: PATCH /calendar-events/{id} (status=CANCELLED) 또는 삭제 — 현재는 자리만
          <Button variant="outline" onClick={() => onDelete(event)}>
            삭제
          </Button>
        )}
      </div>
    </div>
  )
}
