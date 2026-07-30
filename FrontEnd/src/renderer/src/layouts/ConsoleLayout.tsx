import { Outlet } from 'react-router-dom'
import GNB from '@renderer/components/common/GNB'
import { useCurrentSession } from '@renderer/hooks/useCurrentSession'
import type { CurrentSessionContext } from '@renderer/hooks/useCurrentSession'

/**
 * 방송 콘솔(SC-03) 전용 레이아웃.
 * 정보 밀도를 위해 GNB가 아이콘만으로 축소(60px)된 형태.
 *
 * 이 창의 owner 폴링은 여기서 한 번만 돌리고 Outlet context로 자식(BroadcastConsolePage)에
 * 내려준다 — 둘 다 각자 useCurrentSession('owner')를 부르면 같은 창에서 폴링이 두 배가 된다.
 */
export default function ConsoleLayout(): React.JSX.Element {
  const currentSession = useCurrentSession('owner')
  return (
    <div className="flex h-screen">
      <GNB
        collapsed
        live={currentSession.session !== null}
        liveSessionId={currentSession.session?.sessionId}
      />
      <main className="flex-1 overflow-hidden">
        <Outlet context={currentSession satisfies CurrentSessionContext} />
      </main>
    </div>
  )
}
