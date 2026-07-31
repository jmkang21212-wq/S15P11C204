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

/** 그리드(CalendarMonth)·목록(AgendaListView) 두 본문이 공유하는 상단 컨트롤. */
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
    <div className="mb-4 flex items-center gap-3 px-6 pt-6">
      <div className="flex rounded-lg bg-muted p-0.5">
        {VIEW_TABS.map((tab) => (
          <button
            key={tab.mode}
            type="button"
            onClick={() => onViewModeChange(tab.mode)}
            className={cn(
              'rounded-md px-3.5 py-1.5 text-xs font-semibold',
              viewMode === tab.mode
                ? 'bg-card shadow-sm'
                : 'text-muted-foreground hover:text-foreground'
            )}
          >
            {tab.label}
          </button>
        ))}
        {/* 주간 뷰는 준비 중 — 비활성 표시만 유지 */}
        <span className="cursor-not-allowed px-3.5 py-1.5 text-xs font-semibold text-muted-foreground/50">
          주
        </span>
      </div>
      <div className="flex items-center gap-2">
        <Button variant="outline" size="icon-sm" onClick={onPrevMonth} aria-label="이전 달">
          ◀
        </Button>
        <span className="text-base font-bold">
          {year}년 {month + 1}월
        </span>
        <Button variant="outline" size="icon-sm" onClick={onNextMonth} aria-label="다음 달">
          ▶
        </Button>
      </div>
      <span className="rounded-lg border border-dashed px-3 py-1.5 text-xs text-muted-foreground">
        추천 시간대 보기 (준비 중)
      </span>
      <Button
        className="ml-auto"
        size="sm"
        onClick={onAddEvent}
        disabled={addDisabled}
        title={addDisabled ? '연동된 방송 플랫폼이 없어요. 연동 후 이용해 주세요.' : undefined}
      >
        + 일정 추가
      </Button>
    </div>
  )
}
