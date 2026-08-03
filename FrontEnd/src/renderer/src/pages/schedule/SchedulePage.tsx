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
import { listPromises, updatePromise } from '@renderer/api/promise'
import CalendarHeader, { type ScheduleViewMode } from './components/CalendarHeader'
import CalendarMonth from './components/CalendarMonth'
import AgendaListView from './components/AgendaListView'
import EventDetail from './components/EventDetail'
import EventForm from './components/EventForm'
import AnnouncementDraft from './components/AnnouncementDraft'
import UnscheduledPromises from './components/UnscheduledPromises'
import { getMonthGridDays, shiftEventToDate, toDateKey } from './components/calendarGrid'
import type { ScheduleDragPayload } from './components/scheduleDnd'
import type { CalendarEvent, UpdateCalendarEventInput } from '@renderer/types/schedule'

/** 보고 있는 달이 이번 달이면 오늘, 아니면 1일 — EventForm에서 날짜를 바로 바꿀 수 있어 기본값일 뿐이다. */
function defaultEventDay(year: number, month: number): number {
  const today = new Date()
  return today.getFullYear() === year && today.getMonth() === month ? today.getDate() : 1
}

/** BE `calendar_events.title`이 VARCHAR(200)이라 약속 내용이 길면 제목으로 못 쓴다. */
const EVENT_TITLE_MAX = 200

/** 전환 시 채워 넣을 기본 방송 시간. EventForm에서 바로 고칠 수 있어 출발값일 뿐이다. */
const DEFAULT_START_HOUR = 21
const DEFAULT_END_HOUR = 23

/**
 * 약속을 옮겨 담은 신규 일정 초안. 떨어뜨린 날짜에 기본 시간대를 얹고 제목은 약속 내용을 쓴다.
 *
 * `BROADCAST`로 만드는 이유는 선택지가 그것뿐이기 때문이다 — `event_type` CHECK가
 * `BROADCAST | DAY_OFF`만 허용하고 약속은 휴방이 아니다. 그래서 `platform_account_id`가
 * NOT NULL 강제되며(같은 CHECK), 연동 계정이 없으면 전환 자체가 불가능하다.
 */
function promiseEvent(
  dateKey: string,
  content: string,
  platformAccountId: number | null
): CalendarEvent {
  const [year, month, day] = dateKey.split('-').map(Number)
  return {
    calendarEventId: -1,
    platformAccountId,
    eventType: 'BROADCAST',
    title: content.slice(0, EVENT_TITLE_MAX),
    // 잘려나간 내용을 잃지 않도록 원문을 메모에 남긴다.
    memo: content.length > EVENT_TITLE_MAX ? content : null,
    startAt: new Date(year, month - 1, day, DEFAULT_START_HOUR, 0, 0).toISOString(),
    endAt: new Date(year, month - 1, day, DEFAULT_END_HOUR, 0, 0).toISOString(),
    status: 'SCHEDULED'
  }
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
 *
 * 약속은 두 갈래로 들어온다. 기한이 있는 것은 캘린더 조회에 얹혀 오고(`data.promises`),
 * 기한이 없는 것은 `GET /promises?status=PENDING`으로 따로 가져온다 — 캘린더 쿼리가
 * `due_date IS NOT NULL`로 걸러내기 때문이다.
 *
 * 드래그는 끌어온 것이 무엇인지에 따라 다른 일을 한다.
 *  - 기한 미정 약속 → 날짜 칸: 시간을 받아 **일정으로 전환**하고 약속은 목록에서 치운다.
 *    약속은 "기한"이고 일정은 "시간 블록"이라 시각이 정해지는 순간 일정이 맞는 그릇이다.
 *  - 캘린더 약속 칩 → 다른 날짜: 기한만 옮긴다(잘못 떨어뜨렸을 때의 복구 경로).
 *  - 일정 칩 → 다른 날짜: 시각·기간은 그대로 두고 날짜만 옮긴다.
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
  const [movingPromiseId, setMovingPromiseId] = useState<number | null>(null)
  const [movingEventId, setMovingEventId] = useState<number | null>(null)
  const [dragError, setDragError] = useState<string | null>(null)
  /** 지금 폼에 열려 있는 일정이 어떤 약속에서 나왔는지. 저장에 성공하면 그 약속을 치운다. */
  const [convertingPromiseId, setConvertingPromiseId] = useState<number | null>(null)

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

  /**
   * 기한 미정 약속. 캘린더 조회는 `due_date IS NOT NULL`만 내려주므로 이 목록은 반드시
   * 별도 조회여야 한다. 확정 직후 상태가 PENDING이라 그것만 가져온다.
   */
  const promiseLoader = useCallback(() => listPromises('PENDING'), [])
  const {
    data: promisePage,
    loading: promisesLoading,
    error: promisesError,
    refetch: refetchPromises
  } = useAsyncData(promiseLoader)

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
    setConvertingPromiseId(null)
  }

  const handleAddEvent = (): void => {
    setSelectedEvent(blankEvent(viewYear, viewMonth, platformAccount?.platformAccountId ?? null))
    setMode('edit')
    setNewEventNonce((n) => n + 1)
    setDraftContent(null)
    setMutationError(null)
    setConvertingPromiseId(null)
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
      // 약속에서 전환된 일정이면 원래 약속을 치운다. 일정 저장이 성공한 뒤에만 한다.
      if (convertingPromiseId !== null) {
        await tidyConvertedPromise(convertingPromiseId)
        setConvertingPromiseId(null)
      }
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
    // 전환을 취소한 것이므로 약속은 손대지 않고 미정 목록에 그대로 남는다.
    setConvertingPromiseId(null)
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

  /**
   * 이미 기한이 있는 약속 칩을 다른 날짜로 옮긴다. `PatchValue` 계약상 바뀌는 필드만
   * 보내야 하므로 dueDate 하나만 담는다. 잘못 떨어뜨렸을 때 되돌리는 경로이기도 하다.
   */
  const movePromiseDueDate = async (promiseId: number, dateKey: string): Promise<void> => {
    setMovingPromiseId(promiseId)
    setDragError(null)
    try {
      await updatePromise(promiseId, { dueDate: dateKey })
      refetch()
      refetchPromises()
    } catch (e) {
      setDragError(e instanceof ApiError ? e.message : '약속 기한을 옮기지 못했어요.')
    } finally {
      setMovingPromiseId(null)
    }
  }

  /**
   * 기한 미정 약속을 일정으로 전환한다 — 여기서는 폼을 열기만 하고, 실제 생성은
   * `handleSave`가 한다. 시각까지 받아야 `calendar_events`를 만들 수 있고(`end_at > start_at`),
   * 사용자가 취소하면 약속이 그대로 남아야 하므로 드롭 시점에는 아무것도 쓰지 않는다.
   */
  const startPromiseConversion = (promiseId: number, dateKey: string): void => {
    const promise = unscheduledPromises.find((p) => p.promiseId === promiseId)
    if (!promise) return

    // BROADCAST 일정은 platform_account_id가 NOT NULL이라 연동 없이는 만들 수 없다.
    if (platformAccount === null) {
      setDragError('일정으로 만들려면 플랫폼 연동이 먼저 필요해요.')
      return
    }

    setConvertingPromiseId(promiseId)
    setDragError(null)
    setSelectedEvent(promiseEvent(dateKey, promise.content, platformAccount.platformAccountId))
    setMode('edit')
    setNewEventNonce((n) => n + 1)
    setDraftContent(null)
    setMutationError(null)
  }

  /**
   * 일정을 다른 날짜로 옮긴다. 시각·기간은 유지하고 날짜만 바꾼다(`shiftEventToDate`).
   *
   * 상세 폼의 저장 경로(`diffEventPatch`)를 타지 않고 여기서 패치를 직접 만든다 — 바뀌는
   * 것이 startAt·endAt 둘뿐임이 확실하고, 폼을 열지 않고 끝내는 흐름이기 때문이다.
   */
  const moveEventToDate = async (calendarEventId: number, dateKey: string): Promise<void> => {
    const target = visibleEvents.find((e) => e.calendarEventId === calendarEventId)
    if (!target) return
    // 같은 날짜에 다시 떨어뜨린 경우. 값이 그대로라 요청할 이유가 없다.
    if (toDateKey(new Date(target.startAt)) === dateKey) return

    setMovingEventId(calendarEventId)
    setDragError(null)
    try {
      const shifted = shiftEventToDate(target.startAt, target.endAt, dateKey)
      const saved = await updateCalendarEvent(calendarEventId, shifted)
      // 상세 패널이 같은 일정을 열어두고 있으면 옮긴 값으로 맞춘다.
      setSelectedEvent((current) =>
        current?.calendarEventId === calendarEventId ? saved : current
      )
      refetch()
    } catch (e) {
      setDragError(e instanceof ApiError ? e.message : '일정을 옮기지 못했어요.')
    } finally {
      setMovingEventId(null)
    }
  }

  const handleDropItem = (payload: ScheduleDragPayload, dateKey: string): void => {
    if (movingPromiseId !== null || movingEventId !== null) return
    if (payload.kind === 'event') {
      void moveEventToDate(payload.id, dateKey)
      return
    }
    if (payload.source === 'unscheduled') {
      startPromiseConversion(payload.id, dateKey)
      return
    }
    void movePromiseDueDate(payload.id, dateKey)
  }

  /**
   * 전환된 약속을 목록에서 치운다. 일정이 그 역할을 이어받았으므로 미정 목록에 남으면
   * 같은 약속을 또 끌어다 놓게 된다.
   *
   * `DISMISSED`가 맞는 상태지만 BE가 확정(PENDING) 이후의 DISMISSED 전이를 막아 둔 상태다
   * (`PromiseStatus.isPatchTarget`에 없고 `dismiss()`는 `requireDetected()`). 그래서
   * 차선으로 `ON_HOLD`를 쓴다 — 우리 미정 목록은 PENDING만 조회하므로 목록에서는 빠지고,
   * 대시보드 미이행 집계에는 남아 "아직 안 지킨 약속"이라는 사실은 보존된다.
   * BE가 열리면 이 값만 'DISMISSED'로 바꾸면 된다.
   */
  const tidyConvertedPromise = async (promiseId: number): Promise<void> => {
    try {
      await updatePromise(promiseId, { status: 'ON_HOLD' })
    } catch {
      // 일정은 이미 만들어졌다. 정리 실패로 저장을 되돌리지 않고 사실만 알린다.
      setDragError('일정은 만들었지만 약속을 목록에서 치우지 못했어요.')
    } finally {
      refetchPromises()
    }
  }

  // BE 조회 쿼리(findRange)에 status 필터가 없어 취소된 일정도 함께 내려온다 — 화면에서 거른다.
  const visibleEvents = (data?.items ?? []).filter((e) => e.status !== 'CANCELLED')
  const promises = data?.promises ?? []

  // PENDING 전체에서 기한 없는 것만 남긴다 — BE에 "기한 미정" 필터가 없어 여기서 거른다.
  const pendingItems = promisePage?.items ?? []
  const unscheduledPromises = pendingItems.filter((p) => p.dueDate === null)
  // 한 페이지(100건) 상한에 걸려 못 가져온 나머지. 다음 조회에서 이어서 보인다.
  const hiddenPromiseCount = Math.max(0, (promisePage?.totalElements ?? 0) - pendingItems.length)

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

        {/* 드래그 결과 오류는 조작이 일어난 캘린더 쪽에 붙인다 (약속·일정 공용) */}
        {dragError && <p className="px-6 pb-2 text-xs text-destructive">{dragError}</p>}

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
            onDropItem={handleDropItem}
            movingPromiseId={movingPromiseId}
            movingEventId={movingEventId}
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

      {/*
        기한 미정 약속이 드래그 소스라 패널은 상시 표시한다 — 일정을 선택했을 때만 열면
        약속을 끌어다 놓을 출발점 자체가 사라진다. 일정 상세/폼은 그 아래에 이어 붙는다.
      */}
      <aside className="flex w-[344px] shrink-0 flex-col overflow-y-auto border-l bg-card">
        <UnscheduledPromises
          promises={unscheduledPromises}
          loading={promisesLoading}
          error={promisesError}
          hiddenCount={hiddenPromiseCount}
          dropTargetAvailable={viewMode === 'month'}
          movingPromiseId={movingPromiseId}
          onRetry={refetchPromises}
        />

        {selectedEvent && (
          <div className="flex flex-col border-t">
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
          </div>
        )}
      </aside>
    </div>
  )
}
