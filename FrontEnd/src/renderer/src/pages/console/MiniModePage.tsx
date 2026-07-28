import { useSearchParams } from 'react-router-dom'
import { useLiveSnapshot } from '@renderer/hooks/useLiveSnapshot'
import MiniWidget from './components/MiniWidget'

const DEFAULT_SESSION_ID = 9002

/**
 * SC-03 부속: 미니 모드 위젯 라우트 미리보기.
 * 추후 Electron 메인 프로세스가 항상-위 별도 창으로 이 라우트를 로드한다.
 * 미리보기: `#/console/mini` (기본) / `#/console/mini?state=high` (HIGH 강조)
 */
export default function MiniModePage(): React.JSX.Element | null {
  const [params] = useSearchParams()
  const { data } = useLiveSnapshot(DEFAULT_SESSION_ID)

  if (!data) return null

  const isHigh = params.get('state') === 'high'

  // 미리보기용 백드롭 — 실제 창에서는 위젯 자체가 창 전체
  return (
    <div className="flex h-screen items-center justify-center bg-neutral-800 p-6">
      <MiniWidget data={data} isHigh={isHigh} />
    </div>
  )
}
