import { Button } from '@renderer/components/ui/button'
import { cn } from '@renderer/lib/utils'

export type ScheduleViewMode = 'month' | 'list'

interface CalendarHeaderProps {
  year: number
  month: number // 0-indexed
  viewMode: ScheduleViewMode
  onViewModeChange: (mode: ScheduleViewMode) => void
  onPrevMonth: () => void
  onNextMonth: () => void
  onAddEvent: () => void
  /** 연동된 플랫폼 계정이 없어 새 일정을 만들 수 없을 때. */
  addDisabled?: boolean
}

const VIEW_TABS: { mode: ScheduleViewMode; label: string }[] = [
  { mode: 'month', label: '월' },
  { mode: 'list', label: '목록' }
]

/**
 * 그리드(CalendarMonth)·목록(AgendaListView) 두 본문이 공유하는 상단 컨트롤.
 *
 * 뷰 전환과 연·월 이동만 남기고 준비 중 표시는 두지 않는다. 대신 남은 두 컨트롤을
 * 본문(카드·날짜 그리드) 눈높이에 맞게 키워, 빈 자리가 구멍처럼 보이지 않게 한다.
 */
export default function CalendarHeader({
  year,
  month,
  viewMode,
  onViewModeChange,
  onPrevMonth,
  onNextMonth,
  onAddEvent,
  addDisabled = false
}: CalendarHeaderProps): React.JSX.Element {
  return (
    <div className="mb-4 flex items-center gap-4 px-6 pt-6">
      {/*
        양옆을 같은 비율로 늘려 가운데 그룹을 화면 중앙에 고정한다. `ml-auto`로 밀면
        중앙이 "+ 일정 추가" 버튼 폭만큼 왼쪽으로 밀려 정렬이 어긋난다.
      */}
      <div className="flex flex-1 justify-start">
        <div className="flex rounded-lg bg-muted p-1">
          {VIEW_TABS.map((tab) => (
            <button
              key={tab.mode}
              type="button"
              onClick={() => onViewModeChange(tab.mode)}
              className={cn(
                'rounded-md px-6 py-2 text-sm font-semibold',
                viewMode === tab.mode
                  ? 'bg-card shadow-sm'
                  : 'text-muted-foreground hover:text-foreground'
              )}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {/* 연·월 이동 — 월 라벨에 최소 폭을 줘서 자릿수가 바뀔 때 화살표가 흔들리지 않게 한다 */}
      <div className="flex shrink-0 items-center gap-3">
        <Button variant="outline" size="icon" onClick={onPrevMonth} aria-label="이전 달">
          ◀
        </Button>
        <span className="min-w-32 text-center text-title font-bold">
          {year}년 {month + 1}월
        </span>
        <Button variant="outline" size="icon" onClick={onNextMonth} aria-label="다음 달">
          ▶
        </Button>
      </div>

      <div className="flex flex-1 justify-end">
        <Button
          onClick={onAddEvent}
          disabled={addDisabled}
          title={addDisabled ? '연동된 방송 플랫폼이 없어요. 연동 후 이용해 주세요.' : undefined}
        >
          + 일정 추가
        </Button>
      </div>
    </div>
  )
}
