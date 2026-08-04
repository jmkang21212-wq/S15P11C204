import { Outlet } from 'react-router-dom'
import GNB from '@renderer/components/common/GNB'
import { useCurrentSession } from '@renderer/hooks/useCurrentSession'
import { useClipWorker } from '@renderer/hooks/useClipWorker'

/**
 * 스트리머 로그인 후 대부분의 화면이 공유하는 기본 레이아웃.
 * 좌측 GNB(232px) + 우측 콘텐츠 영역(<Outlet/>) 구조.
 */
export default function AppLayout(): React.JSX.Element {
  const { session } = useCurrentSession('owner')
  // 앱이 뜨면 '/'가 항상 대시보드(이 레이아웃)로 리다이렉트되므로, 부팅 시 클립 작업을
  // 확인하는 자리로 적합하다 — 콘솔로 바로 이동해도 이 마운트가 먼저 일어난다.
  useClipWorker()
  return (
    <div className="flex h-screen">
      <GNB live={session !== null} liveSessionId={session?.sessionId} />
      <main className="flex-1 overflow-auto">
        <Outlet />
      </main>
    </div>
  )
}
