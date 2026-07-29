import { useCallback, useState } from 'react'
import { useAsyncData } from '@renderer/hooks/useAsyncData'
import { buildMockAnnouncement, getCalendarEvents } from '@renderer/api/schedule'
import { Skeleton } from '@renderer/components/ui/skeleton'
import { ErrorState } from '@renderer/components/common/StateView'
import CalendarMonth from './components/CalendarMonth'
import EventForm from './components/EventForm'
import AnnouncementDraft from './components/AnnouncementDraft'
import type { CalendarEvent } from '@renderer/types/schedule'

/** "+ 일정 추가"용 빈 일정 (calendarEventId 음수 = 신규) */
function blankEvent(year: number, month: number): CalendarEvent {
  const start = new Date(year, month, 1, 21, 0, 0)
  const end = new Date(year, month, 1, 23, 0, 0)
  return {
    calendarEventId: -1,
    platformAccountId: 15,
    eventType: 'BROADCAST',
    title: '',
    memo: null,
    startAt: start.toISOString(),
    endAt: end.toISOString(),
    status: 'SCHEDULED'
  }
}

/**
 * SC-04 일정 캘린더 — 월 캘린더 + 일정 등록/수정 + AI 공지 초안.
 * 현재는 mock 데이터로 렌더한다. (주 뷰·추천 시간대는 준비 중)
 */
export default function SchedulePage(): React.JSX.Element {
  const [viewYear, setViewYear] = useState(2026)
  const [viewMonth, setViewMonth] = useState(6) // 0-indexed = 7월
  const [selectedEvent, setSelectedEvent] = useState<CalendarEvent | null>(null)
  const [draftContent, setDraftContent] = useState<string | null>(null)

  const from = new Date(viewYear, viewMonth, 1).toISOString()
  const to = new Date(viewYear, viewMonth + 1, 1).toISOString()
  const loader = useCallback(() => getCalendarEvents(from, to), [from, to])
  const { data, loading, error, refetch } = useAsyncData(loader)

  const moveMonth = (delta: number): void => {
    const d = new Date(viewYear, viewMonth + delta, 1)
    setViewYear(d.getFullYear())
    setViewMonth(d.getMonth())
  }

  const handleSelectEvent = (event: CalendarEvent): void => {
    setSelectedEvent(event)
    setDraftContent(null)
  }

  const handleAddEvent = (): void => {
    setSelectedEvent(blankEvent(viewYear, viewMonth))
    setDraftContent(null)
  }

  const handleSave = (event: CalendarEvent): void => {
    // TODO: POST/PATCH /calendar-events (현재는 저장 성공 가정)
    setDraftContent(buildMockAnnouncement(event))
  }

  const handleClose = (): void => {
    setSelectedEvent(null)
    setDraftContent(null)
  }

  const handleDelete = (_event: CalendarEvent): void => {
    // TODO: 일정 취소/삭제 API 연결 (현재는 로직 없음 — UI 자리만)
  }

  return (
    <div className="flex h-full">
      {error ? (
        <div className="flex flex-1 items-center justify-center">
          <ErrorState message="일정을 불러오지 못했어요." onRetry={refetch} />
        </div>
      ) : loading || !data ? (
        <div className="flex flex-1 flex-col gap-3 p-8">
          <Skeleton className="h-8 w-48" />
          <Skeleton className="h-full w-full rounded-xl" />
        </div>
      ) : (
        <CalendarMonth
          year={viewYear}
          month={viewMonth}
          events={data}
          selectedEventId={selectedEvent?.calendarEventId ?? null}
          onPrevMonth={() => moveMonth(-1)}
          onNextMonth={() => moveMonth(1)}
          onSelectEvent={handleSelectEvent}
          onAddEvent={handleAddEvent}
        />
      )}

      {/* 사이드: 일정 등록/수정 + 공지 초안 */}
      <aside className="flex w-[344px] shrink-0 flex-col border-l bg-card">
        <EventForm
          key={selectedEvent?.calendarEventId ?? 'empty'}
          event={selectedEvent}
          onSave={handleSave}
          onClose={handleClose}
          onDelete={handleDelete}
        />
        {draftContent && (
          <div className="px-5 pb-5">
            <AnnouncementDraft content={draftContent} />
          </div>
        )}
      </aside>
    </div>
  )
}
