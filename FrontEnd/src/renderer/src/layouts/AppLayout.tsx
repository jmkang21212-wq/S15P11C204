import { Outlet } from 'react-router-dom'
import GNB from '@renderer/components/common/GNB'
import { useCurrentSession } from '@renderer/hooks/useCurrentSession'

/**
 * 스트리머 로그인 후 대부분의 화면이 공유하는 기본 레이아웃.
 * 좌측 GNB(232px) + 우측 콘텐츠 영역(<Outlet/>) 구조.
 */
export default function AppLayout(): React.JSX.Element {
  const { session } = useCurrentSession('owner')
  return (
    <div className="flex h-screen">
      <GNB live={session !== null} liveSessionId={session?.sessionId} />
      <main className="flex-1 overflow-auto">
        <Outlet />
      </main>
    </div>
  )
}
