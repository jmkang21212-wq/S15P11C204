import { useState } from 'react'
import { Button } from '@renderer/components/ui/button'
import { Input } from '@renderer/components/ui/input'
import { Label } from '@renderer/components/ui/label'
import { cn } from '@renderer/lib/utils'
import type { CalendarEvent, CalendarEventType } from '@renderer/types/schedule'

interface EventFormProps {
  event: CalendarEvent
  /** BROADCAST로 새로 전환할 때 채울 대표 연동 계정. 없으면 null. */
  primaryPlatformAccountId: number | null
  /** 저장 요청 진행 중이면 버튼을 잠근다. */
  saving?: boolean
  /** 직전 저장 실패 메시지 (BE ApiError.message). */
  error?: string | null
  onSave: (event: CalendarEvent) => void
  onClose: () => void
}

function toTimeInput(iso: string): string {
  const d = new Date(iso)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function toDateInput(iso: string): string {
  const d = new Date(iso)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/** `<input type="date">` 값(yyyy-MM-dd)과 `<input type="time">` 값(HH:mm)을 로컬 기준으로 합친다. */
function combineDateTime(date: string, time: string): string {
  const [y, m, d] = date.split('-').map(Number)
  const [h, min] = time.split(':').map(Number)
  return new Date(y, m - 1, d, h, min, 0, 0).toISOString()
}

/** DAY_OFF는 시간 UI가 없어 하루 종일(자정~다음 자정)로 고정한다. */
function dayOffRange(date: string): { startAt: string; endAt: string } {
  const [y, m, d] = date.split('-').map(Number)
  return {
    startAt: new Date(y, m - 1, d).toISOString(),
    endAt: new Date(y, m - 1, d + 1).toISOString()
  }
}

/**
 * 선택된 일정이 바뀌면 폼이 초기화돼야 하므로, 부모(SchedulePage)가
 * `key`(일정 id, 신규는 별도 nonce)로 이 컴포넌트를 매번 새로 마운트한다.
 * 그래서 아래 초기값은 effect 없이 한 번만 평가되면 된다.
 */
export default function EventForm({
  event,
  primaryPlatformAccountId,
  saving = false,
  error = null,
  onSave,
  onClose
}: EventFormProps): React.JSX.Element {
  const [eventType, setEventType] = useState<CalendarEventType>(event.eventType)
  const [platformAccountId, setPlatformAccountId] = useState<number | null>(
    event.platformAccountId ?? primaryPlatformAccountId
  )
  const [title, setTitle] = useState(event.title)
  const [date, setDate] = useState(toDateInput(event.startAt))
  const [startTime, setStartTime] = useState(toTimeInput(event.startAt))
  const [endTime, setEndTime] = useState(toTimeInput(event.endAt))
  const [memo, setMemo] = useState(event.memo ?? '')

  /** BE는 DAY_OFF에 platformAccountId=null을 허용·기대한다. BROADCAST로 되돌리면 대표 계정을 다시 채운다. */
  const handleTypeChange = (t: CalendarEventType): void => {
    setEventType(t)
    if (t === 'DAY_OFF') {
      setPlatformAccountId(null)
    } else if (platformAccountId === null) {
      setPlatformAccountId(primaryPlatformAccountId)
    }
  }

  const handleSave = (): void => {
    const { startAt, endAt } =
      eventType === 'BROADCAST'
        ? { startAt: combineDateTime(date, startTime), endAt: combineDateTime(date, endTime) }
        : dayOffRange(date)
    onSave({
      ...event,
      eventType,
      platformAccountId,
      title,
      memo: memo || null,
      startAt,
      endAt
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
            onClick={() => handleTypeChange(t)}
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

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="date">날짜</Label>
        <Input id="date" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
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

      {error && <p className="text-xs text-destructive">{error}</p>}

      <Button className="w-full" onClick={handleSave} disabled={saving}>
        {saving ? '저장 중…' : '저장'}
      </Button>
    </div>
  )
}
