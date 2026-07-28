import { useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useAsyncData } from '@renderer/hooks/useAsyncData'
import { getLiveSnapshot } from '@renderer/api/console'
import MiniWidget from './components/MiniWidget'

const DEFAULT_SESSION_ID = 9002

/**
 * SC-03 부속: 미니 모드 창의 내용.
 * Electron 메인 프로세스가 항상-위 별도 창(340×220)으로 이 라우트를 로드한다.
 * 미리보기: `#/console/mini` (기본) / `#/console/mini?state=high` (HIGH 강조)
 */
export default function MiniModePage(): React.JSX.Element | null {
  const [params] = useSearchParams()
  const loader = useCallback(() => getLiveSnapshot(DEFAULT_SESSION_ID), [])
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
