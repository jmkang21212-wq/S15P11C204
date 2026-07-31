import { Link, useNavigate } from 'react-router-dom'
import { MonitorPlay } from 'lucide-react'
import { Card } from '@renderer/components/ui/card'
import { Button } from '@renderer/components/ui/button'
import { EmptyState } from '@renderer/components/common/StateView'
import { PATHS } from '@renderer/routes/paths'
import { formatTime } from '@renderer/lib/format'
import type { UpcomingBroadcast } from '@renderer/types/dashboard'

interface UpcomingBroadcastCardProps {
  broadcast: UpcomingBroadcast | null
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

  return (
    <Card className="gap-3 p-5">
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold text-muted-foreground">다음 방송</span>
        {broadcast && (
          <span className="font-mono text-xs text-muted-foreground">
            {formatTime(broadcast.startAt)}
          </span>
        )}
      </div>

      <div className="flex flex-1 flex-col">
        {broadcast ? (
          <p className="text-base font-bold">{broadcast.title}</p>
        ) : (
          // 예정 일정이 없어도 막다른 길이 아니다 — 콘솔에서 제목만 넣고 바로 시작할 수 있다.
          <EmptyState
            title="예정된 방송이 없어요"
            className="rounded-lg bg-muted/40 py-6"
            action={
              <Link to={PATHS.schedule} className="text-xs font-medium text-primary">
                일정 추가하기 →
              </Link>
            }
          />
        )}

        <Button size="lg" className="mt-4 w-full" onClick={() => navigate(PATHS.console)}>
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
