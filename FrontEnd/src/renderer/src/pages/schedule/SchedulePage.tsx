import { useCallback, useState } from 'react'
import { useAsyncData } from '@renderer/hooks/useAsyncData'
import { usePrimaryPlatformAccount } from '@renderer/hooks/usePlatformAccounts'
import { ApiError } from '@renderer/api/client'
import {
  buildAnnouncementDraft,
  cancelCalendarEvent,
  createCalendarEvent,
  getCalendarEvents,
  updateCalendarEvent
} from '@renderer/api/schedule'
import { Skeleton } from '@renderer/components/ui/skeleton'
import { ErrorState } from '@renderer/components/common/StateView'
import CalendarMonth from './components/CalendarMonth'
import EventForm from './components/EventForm'
import AnnouncementDraft from './components/AnnouncementDraft'
import type { CalendarEvent, UpdateCalendarEventInput } from '@renderer/types/schedule'

/** "+ 일정 추가"용 빈 일정 (calendarEventId 음수 = 신규) */
function blankEvent(year: number, month: number, platformAccountId: number | null): CalendarEvent {
  const start = new Date(year, month, 1, 21, 0, 0)
  const end = new Date(year, month, 1, 23, 0, 0)
  return {
    calendarEventId: -1,
    platformAccountId,
    eventType: 'BROADCAST',
    title: '',
    memo: null,
    startAt: start.toISOString(),
    endAt: end.toISOString(),
    status: 'SCHEDULED'
  }
}

/**
 * 저장 시점의 원본(수정 전 selectedEvent)과 폼이 만든 값을 비교해 실제로 바뀐
 * 필드만 담는다. BE `PatchValue` 계약상 안 바뀐 필드는 키 자체를 보내면 안 된다.
 */
function diffEventPatch(original: CalendarEvent, updated: CalendarEvent): UpdateCalendarEventInput {
  const patch: UpdateCalendarEventInput = {}
  if (updated.platformAccountId !== original.platformAccountId) {
    patch.platformAccountId = updated.platformAccountId
  }
  if (updated.eventType !== original.eventType) patch.eventType = updated.eventType
  if (updated.title !== original.title) patch.title = updated.title
  if (updated.memo !== original.memo) patch.memo = updated.memo
  if (updated.startAt !== original.startAt) patch.startAt = updated.startAt
  if (updated.endAt !== original.endAt) patch.endAt = updated.endAt
  return patch
}

/**
 * SC-04 일정 캘린더 — 월 캘린더 + 일정 등록/수정/취소 + AI 공지 초안.
 * 공지 초안은 BE 발행 API가 아직 보류라 클라이언트에서 생성한다 (api/schedule.ts 참고).
 */
export default function SchedulePage(): React.JSX.Element {
  const [viewYear, setViewYear] = useState(() => new Date().getFullYear())
  const [viewMonth, setViewMonth] = useState(() => new Date().getMonth())
  const [selectedEvent, setSelectedEvent] = useState<CalendarEvent | null>(null)
  const [draftContent, setDraftContent] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [mutationError, setMutationError] = useState<string | null>(null)

  const { account: platformAccount, loading: platformAccountLoading } = usePrimaryPlatformAccount()

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
    setMutationError(null)
  }

  const handleAddEvent = (): void => {
    setSelectedEvent(blankEvent(viewYear, viewMonth, platformAccount?.platformAccountId ?? null))
    setDraftContent(null)
    setMutationError(null)
  }

  const handleSave = async (event: CalendarEvent): Promise<void> => {
    setSaving(true)
    setMutationError(null)
    try {
      let saved: CalendarEvent
      if (event.calendarEventId < 0) {
        saved = await createCalendarEvent({
          platformAccountId: event.platformAccountId,
          eventType: event.eventType,
          title: event.title,
          memo: event.memo,
          startAt: event.startAt,
          endAt: event.endAt
        })
      } else if (selectedEvent) {
        const patch = diffEventPatch(selectedEvent, event)
        saved =
          Object.keys(patch).length > 0
            ? await updateCalendarEvent(event.calendarEventId, patch)
            : event
      } else {
        saved = event
      }
      setSelectedEvent(saved)
      setDraftContent(buildAnnouncementDraft(saved))
      refetch()
    } catch (e) {
      setMutationError(e instanceof ApiError ? e.message : '일정을 저장하지 못했어요.')
    } finally {
      setSaving(false)
    }
  }

  const handleClose = (): void => {
    setSelectedEvent(null)
    setDraftContent(null)
    setMutationError(null)
  }

  const handleDelete = async (event: CalendarEvent): Promise<void> => {
    setSaving(true)
    setMutationError(null)
    try {
      await cancelCalendarEvent(event.calendarEventId)
      setSelectedEvent(null)
      setDraftContent(null)
      refetch()
    } catch (e) {
      setMutationError(e instanceof ApiError ? e.message : '일정을 취소하지 못했어요.')
    } finally {
      setSaving(false)
    }
  }

  // BE 조회 쿼리(findRange)에 status 필터가 없어 취소된 일정도 함께 내려온다 — 화면에서 거른다.
  const visibleEvents = (data ?? []).filter((e) => e.status !== 'CANCELLED')

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
          events={visibleEvents}
          selectedEventId={selectedEvent?.calendarEventId ?? null}
          onPrevMonth={() => moveMonth(-1)}
          onNextMonth={() => moveMonth(1)}
          onSelectEvent={handleSelectEvent}
          onAddEvent={handleAddEvent}
          addDisabled={!platformAccountLoading && platformAccount === null}
        />
      )}

      {/* 사이드: 일정 등록/수정 + 공지 초안 */}
      <aside className="flex w-[344px] shrink-0 flex-col border-l bg-card">
        <EventForm
          key={selectedEvent?.calendarEventId ?? 'empty'}
          event={selectedEvent}
          primaryPlatformAccountId={platformAccount?.platformAccountId ?? null}
          saving={saving}
          error={mutationError}
          onSave={(event) => void handleSave(event)}
          onClose={handleClose}
          onDelete={(event) => void handleDelete(event)}
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
