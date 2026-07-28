import { Link } from 'react-router-dom'
import { Play } from 'lucide-react'
import { Card } from '@renderer/components/ui/card'
import { Button } from '@renderer/components/ui/button'
import EmptyState from '@renderer/components/common/EmptyState'
import { PATHS } from '@renderer/routes/paths'
import { formatTime } from '@renderer/lib/format'
import type { UpcomingBroadcast } from '@renderer/types/dashboard'

interface UpcomingBroadcastCardProps {
  broadcast: UpcomingBroadcast | null
}

export default function UpcomingBroadcastCard({
  broadcast,
}: UpcomingBroadcastCardProps): React.JSX.Element {
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

      {broadcast ? (
        <div className="flex flex-1 flex-col">
          <p className="text-base font-bold">{broadcast.title}</p>
          {/* TODO: POST /broadcast-sessions 로 세션 시작 후 콘솔로 진입 */}
          <Button asChild size="lg" className="mt-4 w-full">
            <Link to={PATHS.console}>
              <Play aria-hidden />
              지금 방송 시작
            </Link>
          </Button>
          <Link
            to={PATHS.schedule}
            className="mt-3 text-center text-xs font-medium text-muted-foreground hover:text-foreground"
          >
            일정 관리 →
          </Link>
        </div>
      ) : (
        <EmptyState
          message="예정된 방송이 없어요"
          action={
            <Link to={PATHS.schedule} className="text-xs font-medium text-primary">
              일정 추가하기 →
            </Link>
          }
        />
      )}
    </Card>
  )
}
