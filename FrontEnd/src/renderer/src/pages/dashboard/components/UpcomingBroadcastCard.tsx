import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Play } from 'lucide-react'
import { Card } from '@renderer/components/ui/card'
import { Button } from '@renderer/components/ui/button'
import { EmptyState } from '@renderer/components/common/StateView'
import { PATHS } from '@renderer/routes/paths'
import { formatTime } from '@renderer/lib/format'
import { ApiError } from '@renderer/api/client'
import { fetchCurrentSession, startSession } from '@renderer/api/broadcastSessions'
import type { UpcomingBroadcast } from '@renderer/types/dashboard'
import type { PlatformAccount } from '@renderer/types/platformAccount'

/** BE 정규식(`^[a-z]{2}(-[A-Z]{2})?$`)을 만족하는 기본값. TODO: 설정 화면 도입 시 사용자 값으로. */
const DEFAULT_LANGUAGE = 'ko'

interface UpcomingBroadcastCardProps {
  broadcast: UpcomingBroadcast | null
  /** 방송을 시작할 연동 계정. 조회 중이거나 없으면 null. */
  platformAccount: PlatformAccount | null
  platformAccountLoading: boolean
}

export default function UpcomingBroadcastCard({
  broadcast,
  platformAccount,
  platformAccountLoading
}: UpcomingBroadcastCardProps): React.JSX.Element {
  const navigate = useNavigate()
  const [starting, setStarting] = useState(false)
  const [startError, setStartError] = useState<string | null>(null)

  const goToConsole = (sessionId: number): void => {
    navigate(PATHS.consoleSession.replace(':sessionId', String(sessionId)))
  }

  const handleStart = async (): Promise<void> => {
    if (!broadcast || !platformAccount) return
    setStarting(true)
    setStartError(null)
    try {
      const result = await startSession({
        platformAccountId: platformAccount.platformAccountId,
        calendarEventId: broadcast.calendarEventId,
        title: broadcast.title,
        category: null,
        language: DEFAULT_LANGUAGE
      })
      goToConsole(result.sessionId)
    } catch (e) {
      if (e instanceof ApiError) {
        if (e.code === 'SESSION_ALREADY_LIVE') {
          // 이미 진행 중인 세션이 있다는 뜻 — 그 세션 콘솔로 바로 보낸다.
          const current = await fetchCurrentSession()
          if (current) {
            goToConsole(current.sessionId)
            return
          }
          setStartError('이미 진행 중인 방송이 있어요.')
        } else if (
          e.code === 'PLATFORM_REAUTH_REQUIRED' ||
          e.code === 'PLATFORM_ACCOUNT_NOT_FOUND'
        ) {
          navigate(PATHS.platformConnect)
          return
        } else if (e.code === 'CALENDAR_EVENT_STATE_CONFLICT') {
          setStartError('이미 시작된 일정이에요. 새로고침해 주세요.')
        } else {
          setStartError(e.message)
        }
      } else {
        setStartError('방송을 시작하지 못했어요.')
      }
    } finally {
      setStarting(false)
    }
  }

  const needsPlatformAccount = !platformAccountLoading && platformAccount === null

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

          {needsPlatformAccount ? (
            <div className="mt-4 flex flex-col gap-2">
              <p className="text-center text-xs text-muted-foreground">
                연동된 방송 플랫폼이 없어 시작할 수 없어요.
              </p>
              <Button asChild size="lg" variant="outline" className="w-full">
                <Link to={PATHS.platformConnect}>플랫폼 연동하러 가기</Link>
              </Button>
            </div>
          ) : (
            <>
              <Button
                size="lg"
                className="mt-4 w-full"
                disabled={starting || platformAccountLoading}
                onClick={() => void handleStart()}
              >
                <Play aria-hidden />
                {starting ? '시작하는 중…' : '지금 방송 시작'}
              </Button>
              {startError && (
                <p className="mt-2 text-center text-xs text-destructive">{startError}</p>
              )}
            </>
          )}

          <Link
            to={PATHS.schedule}
            className="mt-3 text-center text-xs font-medium text-muted-foreground hover:text-foreground"
          >
            일정 관리 →
          </Link>
        </div>
      ) : (
        <EmptyState
          title="예정된 방송이 없어요"
          className="rounded-lg bg-muted/40 py-8"
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
