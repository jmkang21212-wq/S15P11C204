import { useCallback, useState } from 'react'
import { useAsyncData } from '@renderer/hooks/useAsyncData'
import { usePrimaryPlatformAccount } from '@renderer/hooks/usePlatformAccounts'
import { ApiError } from '@renderer/api/client'
import {
  buildAnnouncementDraft,
  cancelCalendarEvent,
  createCalendarEvent,
  getCalendarSchedule,
  updateCalendarEvent
} from '@renderer/api/schedule'
import { Skeleton } from '@renderer/components/ui/skeleton'
import { ErrorState } from '@renderer/components/common/StateView'
import CalendarHeader, { type ScheduleViewMode } from './components/CalendarHeader'
import CalendarMonth from './components/CalendarMonth'
import AgendaListView from './components/AgendaListView'
import EventDetail from './components/EventDetail'
import EventForm from './components/EventForm'
import AnnouncementDraft from './components/AnnouncementDraft'
import { getMonthGridDays } from './components/calendarGrid'
import type { CalendarEvent, UpdateCalendarEventInput } from '@renderer/types/schedule'

/** 보고 있는 달이 이번 달이면 오늘, 아니면 1일 — EventForm에서 날짜를 바로 바꿀 수 있어 기본값일 뿐이다. */
function defaultEventDay(year: number, month: number): number {
  const today = new Date()
  return today.getFullYear() === year && today.getMonth() === month ? today.getDate() : 1
}

/** "+ 일정 추가"용 빈 일정 (calendarEventId 음수 = 신규) */
function blankEvent(year: number, month: number, platformAccountId: number | null): CalendarEvent {
  const day = defaultEventDay(year, month)
  const start = new Date(year, month, day, 21, 0, 0)
  const end = new Date(year, month, day, 23, 0, 0)
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
 * SC-04 일정 캘린더 — 월 캘린더/목록 + 일정 조회·등록·수정·취소 + AI 공지 초안.
 * 공지 초안은 BE 발행 API가 아직 보류라 클라이언트에서 생성한다 (api/schedule.ts 참고).
 * 약속(promises)은 확정/무시 API가 아직 없어 읽기 전용으로만 얹는다.
 */
export default function SchedulePage(): React.JSX.Element {
  const [viewYear, setViewYear] = useState(() => new Date().getFullYear())
  const [viewMonth, setViewMonth] = useState(() => new Date().getMonth())
  const [viewMode, setViewMode] = useState<ScheduleViewMode>('month')
  const [selectedEvent, setSelectedEvent] = useState<CalendarEvent | null>(null)
  const [mode, setMode] = useState<'view' | 'edit'>('view')
  const [newEventNonce, setNewEventNonce] = useState(0)
  const [draftContent, setDraftContent] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [mutationError, setMutationError] = useState<string | null>(null)

  const { account: platformAccount, loading: platformAccountLoading } = usePrimaryPlatformAccount()

  // 그리드에 그려지는 인접 달의 며칠까지 포함해서 조회해야 그 칸의 일정도 함께 뜬다.
  const gridDays = getMonthGridDays(viewYear, viewMonth)
  const from = gridDays[0].toISOString()
  const lastGridDay = gridDays[gridDays.length - 1]
  const to = new Date(
    lastGridDay.getFullYear(),
    lastGridDay.getMonth(),
    lastGridDay.getDate() + 1
  ).toISOString()
  const loader = useCallback(() => getCalendarSchedule(from, to), [from, to])
  const { data, loading, error, refetch } = useAsyncData(loader)

  const moveMonth = (delta: number): void => {
    const d = new Date(viewYear, viewMonth + delta, 1)
    setViewYear(d.getFullYear())
    setViewMonth(d.getMonth())
  }

  const handleSelectEvent = (event: CalendarEvent): void => {
    setSelectedEvent(event)
    setMode('view')
    setDraftContent(null)
    setMutationError(null)
  }

  const handleAddEvent = (): void => {
    setSelectedEvent(blankEvent(viewYear, viewMonth, platformAccount?.platformAccountId ?? null))
    setMode('edit')
    setNewEventNonce((n) => n + 1)
    setDraftContent(null)
    setMutationError(null)
  }

  const handleEditClick = (): void => {
    setMode('edit')
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
      setMode('view')
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
    setMode('view')
    setDraftContent(null)
    setMutationError(null)
  }

  /** 편집 중 닫기: 신규 일정이면 완전히 닫고, 기존 일정이면 저장 없이 상세 보기로 복귀. */
  const handleFormClose = (): void => {
    if (selectedEvent && selectedEvent.calendarEventId < 0) {
      handleClose()
      return
    }
    setMode('view')
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
  const visibleEvents = (data?.items ?? []).filter((e) => e.status !== 'CANCELLED')
  const promises = data?.promises ?? []

  return (
    <div className="flex h-full">
      <div className="flex flex-1 flex-col overflow-hidden">
        <CalendarHeader
          year={viewYear}
          month={viewMonth}
          viewMode={viewMode}
          onViewModeChange={setViewMode}
          onPrevMonth={() => moveMonth(-1)}
          onNextMonth={() => moveMonth(1)}
          onAddEvent={handleAddEvent}
          addDisabled={!platformAccountLoading && platformAccount === null}
        />

        {error ? (
          <div className="flex flex-1 items-center justify-center px-6 pb-6">
            <ErrorState message="일정을 불러오지 못했어요." onRetry={refetch} />
          </div>
        ) : loading || !data ? (
          <div className="flex flex-1 flex-col gap-3 px-6 pb-6">
            <Skeleton className="h-8 w-48" />
            <Skeleton className="h-full w-full rounded-xl" />
          </div>
        ) : viewMode === 'month' ? (
          <CalendarMonth
            year={viewYear}
            month={viewMonth}
            events={visibleEvents}
            promises={promises}
            selectedEventId={selectedEvent?.calendarEventId ?? null}
            onSelectEvent={handleSelectEvent}
          />
        ) : (
          <AgendaListView
            events={visibleEvents}
            promises={promises}
            selectedEventId={selectedEvent?.calendarEventId ?? null}
            onSelectEvent={handleSelectEvent}
          />
        )}
      </div>

      {/* 아무것도 선택 안 됐으면 패널 자체를 숨겨 달력에 폭을 돌려준다. */}
      {selectedEvent && (
        <aside className="flex w-[344px] shrink-0 flex-col border-l bg-card">
          {mode === 'edit' ? (
            <EventForm
              key={
                selectedEvent.calendarEventId < 0
                  ? `new-${newEventNonce}`
                  : selectedEvent.calendarEventId
              }
              event={selectedEvent}
              primaryPlatformAccountId={platformAccount?.platformAccountId ?? null}
              saving={saving}
              error={mutationError}
              onSave={(event) => void handleSave(event)}
              onClose={handleFormClose}
            />
          ) : (
            <EventDetail
              event={selectedEvent}
              onEdit={handleEditClick}
              onClose={handleClose}
              onDelete={(event) => void handleDelete(event)}
              deleting={saving}
              error={mutationError}
            />
          )}
          {draftContent && (
            <div className="px-5 pb-5">
              <AnnouncementDraft content={draftContent} />
            </div>
          )}
        </aside>
      )}
    </div>
  )
}
