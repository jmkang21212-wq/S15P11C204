import { useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useAsyncData } from '@renderer/hooks/useAsyncData'
import { useCurrentSession } from '@renderer/hooks/useCurrentSession'
import { getLiveSnapshot } from '@renderer/api/console'
import MiniWidget from './components/MiniWidget'

/**
 * SC-03 부속: 미니 모드 창의 내용.
 * Electron 메인 프로세스가 항상-위 별도 창(340×220)으로 이 라우트를 로드한다.
 * 미리보기: `#/console/mini` (기본) / `#/console/mini?state=high` (HIGH 강조)
 *
 * 이 창은 별도 BrowserWindow라 인증 토큰이 없다 — API를 직접 부르지 않고
 * main의 IPC 릴레이(session:state)로 진행 중 세션을 받는다(follower 모드).
 */
export default function MiniModePage(): React.JSX.Element | null {
  const [params] = useSearchParams()
  const { session: currentSession } = useCurrentSession('follower')
  const sessionId = currentSession?.sessionId ?? null
  const loader = useCallback(() => {
    if (sessionId === null) return Promise.resolve(null)
    return getLiveSnapshot(sessionId)
  }, [sessionId])
  const { data } = useAsyncData(loader)

  if (!data) return null

  const isHigh = params.get('state') === 'high'

  return (
    <div className="h-screen w-screen">
      <MiniWidget
        data={data}
        isHigh={isHigh}
        onGoConsole={() => window.api.focusMainWindow()}
        onClose={() => window.api.closeMiniWindow()}
      />
    </div>
  )
}
