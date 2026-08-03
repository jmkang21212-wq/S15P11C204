import { Button } from '@renderer/components/ui/button'
import { cn } from '@renderer/lib/utils'
import { formatDate, formatDateTime, formatTime } from '@renderer/lib/format'
import type { CalendarEvent } from '@renderer/types/schedule'

interface EventDetailProps {
  event: CalendarEvent
  onEdit: () => void
  onClose: () => void
  onDelete: (event: CalendarEvent) => void
  deleting?: boolean
  error?: string | null
}

/** endAt은 배타적 다음날 자정이라 1ms를 빼야 실제 마지막 날짜가 나온다. */
function dayOffLabel(startAt: string, endAt: string): string {
  const start = new Date(startAt)
  const lastDay = new Date(new Date(endAt).getTime() - 1)
  const spansMultipleDays = lastDay.toDateString() !== start.toDateString()
  return spansMultipleDays
    ? `${formatDate(startAt)} ~ ${formatDate(lastDay.toISOString())}`
    : formatDate(startAt)
}

/**
 * 일정 조회 전용 화면. `EventForm`(편집)과 분리해 클릭 즉시 편집 모드로 들어가지 않게 한다 —
 * 부모(SchedulePage)가 "수정" 클릭 시에만 편집 화면으로 전환한다.
 */
export default function EventDetail({
  event,
  onEdit,
  onClose,
  onDelete,
  deleting = false,
  error = null
}: EventDetailProps): React.JSX.Element {
  const isBroadcast = event.eventType === 'BROADCAST'

  return (
    <div className="flex flex-col gap-4 p-5">
      <div className="flex items-center justify-between">
        <span className="text-sm font-bold">일정 상세</span>
        <button
          type="button"
          onClick={onClose}
          aria-label="닫기"
          className="text-muted-foreground hover:text-foreground"
        >
          ✕
        </button>
      </div>

      <div className="flex flex-wrap items-center gap-1.5">
        <span
          className={cn(
            'w-fit rounded-lg px-2.5 py-1 text-xs font-semibold',
            isBroadcast ? 'bg-accent text-accent-foreground' : 'bg-muted text-muted-foreground'
          )}
        >
          {isBroadcast ? '방송' : '휴방'}
        </span>
        {/* 취소하면 원래 약속이 "기한 미정"으로 돌아온다 — 그 사실을 미리 알 수 있어야 한다. */}
        {event.promiseId !== null && (
          <span className="w-fit rounded-lg border border-dashed border-warning/40 bg-warning/5 px-2.5 py-1 text-xs font-semibold text-warning">
            🔖 약속에서 생성됨
          </span>
        )}
      </div>

      <p className="text-base font-bold break-words">{event.title}</p>

      <div className="flex flex-col gap-1 text-sm">
        {isBroadcast ? (
          <span>
            {formatDateTime(event.startAt)} - {formatTime(event.endAt)}
          </span>
        ) : (
          <span>{dayOffLabel(event.startAt, event.endAt)}</span>
        )}
      </div>

      <div className="flex flex-col gap-1.5">
        <span className="text-xs font-semibold text-muted-foreground">메모</span>
        <p className="whitespace-pre-wrap text-sm text-muted-foreground">
          {event.memo || '메모 없음'}
        </p>
      </div>

      {error && <p className="text-xs text-destructive">{error}</p>}

      <div className="flex gap-2">
        <Button className="flex-1" onClick={onEdit} disabled={deleting}>
          수정
        </Button>
        <Button variant="outline" onClick={() => onDelete(event)} disabled={deleting}>
          {deleting ? '처리 중…' : '일정 취소'}
        </Button>
      </div>
    </div>
  )
}
