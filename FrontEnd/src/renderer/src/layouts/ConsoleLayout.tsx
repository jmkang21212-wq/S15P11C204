import { Outlet } from 'react-router-dom'
import GNB from '@renderer/components/common/GNB'

/**
 * 방송 콘솔(SC-03) 전용 레이아웃.
 * 정보 밀도를 위해 GNB가 아이콘만으로 축소(60px)된 형태.
 */
export default function ConsoleLayout(): React.JSX.Element {
  return (
    <div className="flex h-screen">
      {/* 콘솔은 방송 진행 중 맥락이므로 LIVE 배너 노출 (추후 실제 세션 상태로 대체) */}
      <GNB collapsed live />
      <main className="flex-1 overflow-hidden">
        <Outlet />
      </main>
    </div>
  )
}
