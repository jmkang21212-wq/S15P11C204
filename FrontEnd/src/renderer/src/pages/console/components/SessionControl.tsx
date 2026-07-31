import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '@renderer/components/ui/button'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger
} from '@renderer/components/ui/alert-dialog'
import { ApiError } from '@renderer/api/client'
import { endSession } from '@renderer/api/broadcastSessions'
import { PATHS } from '@renderer/routes/paths'

interface SessionControlProps {
  sessionId: number
}

/**
 * LAIVE 분석 세션 종료 컨트롤 — `POST /broadcast-sessions/{id}/end`.
 *
 * ObsControl의 "송출 시작/중단"은 OBS 송출만 제어한다. 이름이 겹치면 사용자가
 * "방송 종료"를 눌렀는데 LAIVE 분석은 계속 돌고 있는 상황을 오해하기 쉬워
 * "분석 종료"로 분리해 부른다.
 */
export default function SessionControl({ sessionId }: SessionControlProps): React.JSX.Element {
  const navigate = useNavigate()
  const [ending, setEnding] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const goToReport = (): void => {
    navigate(PATHS.sessionReportDetail.replace(':sessionId', String(sessionId)))
  }

  const handleEnd = async (): Promise<void> => {
    setEnding(true)
    setError(null)
    try {
      await endSession(sessionId)
      goToReport()
    } catch (e) {
      if (e instanceof ApiError && e.code === 'SESSION_ALREADY_ENDED') {
        // 이미 끝난 세션 — 실패로 취급하지 않고 리포트로 보낸다 (멱등 취급).
        goToReport()
        return
      }
      if (e instanceof ApiError && e.code === 'SESSION_NOT_FOUND') {
        navigate(PATHS.dashboard)
        return
      }
      setError(e instanceof ApiError ? e.message : '방송 종료에 실패했어요.')
    } finally {
      setEnding(false)
    }
  }

  return (
    <div className="flex items-center gap-2">
      <AlertDialog>
        <AlertDialogTrigger asChild>
          <Button size="sm" variant="destructive" disabled={ending}>
            {ending ? '종료하는 중…' : '분석 종료'}
          </Button>
        </AlertDialogTrigger>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>방송 분석을 종료할까요?</AlertDialogTitle>
            <AlertDialogDescription>
              종료 후 리포트 생성이 시작됩니다. 이 작업은 되돌릴 수 없어요.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>취소</AlertDialogCancel>
            <AlertDialogAction onClick={() => void handleEnd()}>종료</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
      {error && <span className="text-xs text-destructive">{error}</span>}
    </div>
  )
}
