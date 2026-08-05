import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { CalendarDays, Clock, MonitorPlay } from 'lucide-react'
import { Card } from '@renderer/components/ui/card'
import { Button } from '@renderer/components/ui/button'
import { EmptyState } from '@renderer/components/common/StateView'
import { PATHS } from '@renderer/routes/paths'
import { cn } from '@renderer/lib/utils'
import { formatDate, formatDuration, formatTime } from '@renderer/lib/format'
import type { UpcomingBroadcast } from '@renderer/types/dashboard'

interface UpcomingBroadcastCardProps {
  broadcast: UpcomingBroadcast | null
}

/**
 * 방송일까지 남은 일수. 자정 기준으로 잘라 세므로 "23시 방송"과 "다음날 1시 방송"이
 * 각각 오늘·내일로 나뉜다. 시각 차이로 계산하면 둘 다 "오늘"이 되어 어긋난다.
 */
function daysUntil(startAt: string, today: Date): number {
  const start = new Date(startAt)
  const fromMidnight = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  const toMidnight = new Date(start.getFullYear(), start.getMonth(), start.getDate())
  return Math.round((toMidnight.getTime() - fromMidnight.getTime()) / 86_400_000)
}

function dDayLabel(days: number): string {
  if (days <= 0) return '오늘'
  if (days === 1) return '내일'
  return `D-${days}`
}

/**
 * 대시보드의 "다음 방송" 카드.
 *
 * 방송 시작 자체는 콘솔(SC-03)이 맡는다 — 시작에는 OBS 연결이 필요한데 그 연결 UI가
 * 콘솔에 있어서, 여기서 시작하면 사용자가 두 화면을 오가야 했다. 이 카드는 예정 방송을
 * 알려주고 콘솔로 보내는 역할만 한다.
 */
export default function UpcomingBroadcastCard({
  broadcast
}: UpcomingBroadcastCardProps): React.JSX.Element {
  const navigate = useNavigate()
  // 화면을 열어둔 동안 D-day가 흔들리지 않게 기준 시각을 한 번만 잡는다.
  const [today] = useState(() => new Date())
  const days = broadcast ? daysUntil(broadcast.startAt, today) : null

  return (
    <Card className="gap-3 p-5">
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold text-muted-foreground">다음 방송</span>
        {days !== null && (
          <span
            className={cn(
              'rounded-full px-2 py-0.5 font-mono text-xs font-bold',
              // 오늘·내일은 지금 준비해야 하는 일이라 시선을 끌어야 한다.
              days <= 1 ? 'bg-primary/10 text-primary' : 'text-muted-foreground'
            )}
          >
            {dDayLabel(days)}
          </span>
        )}
      </div>

      <div className="flex flex-1 flex-col">
        {broadcast ? (
          <>
            <p className="text-base font-bold">{broadcast.title}</p>
            {/* 날짜·시간 범위·길이 — 응답에 이미 있는 값이라 별도 조회가 필요 없다 */}
            <div className="mt-2.5 flex flex-col gap-1.5 text-sm text-muted-foreground">
              <span className="flex items-center gap-2">
                <CalendarDays className="size-4 shrink-0" strokeWidth={1.8} aria-hidden />
                {formatDate(broadcast.startAt)}
              </span>
              <span className="flex items-center gap-2">
                <Clock className="size-4 shrink-0" strokeWidth={1.8} aria-hidden />
                <span className="font-mono">
                  {formatTime(broadcast.startAt)} ~ {formatTime(broadcast.endAt)}
                </span>
                <span className="text-muted-foreground/70">
                  · {formatDuration(broadcast.startAt, broadcast.endAt)}
                </span>
              </span>
            </div>
          </>
        ) : (
          // 예정 일정이 없어도 막다른 길이 아니다 — 콘솔에서 제목만 넣고 바로 시작할 수 있다.
          <EmptyState
            title="예정된 방송이 없어요"
            className="mb-4 flex-1 rounded-lg bg-muted/40 py-6"
            action={
              <Link to={PATHS.schedule} className="text-xs font-medium text-primary">
                일정 추가하기 →
              </Link>
            }
          />
        )}

        {/*
          `mt-auto`로 CTA를 카드 아래쪽에 붙인다. 옆 카드(최근 세션 성과)가 요약 문단
          때문에 더 높아서, 이게 없으면 남는 높이가 전부 버튼 아래에 빈칸으로 몰린다.
        */}
        <Button size="lg" className="mt-auto w-full" onClick={() => navigate(PATHS.console)}>
          <MonitorPlay aria-hidden />
          방송 콘솔 열기
        </Button>
        <p className="mt-2 text-center text-micro text-muted-foreground">
          방송 시작은 콘솔에서 해요.
        </p>

        {broadcast && (
          <Link
            to={PATHS.schedule}
            className="mt-3 text-center text-xs font-medium text-muted-foreground hover:text-foreground"
          >
            일정 관리 →
          </Link>
        )}
      </div>
    </Card>
  )
}
