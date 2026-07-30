import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useLiveSnapshot } from '@renderer/hooks/useLiveSnapshot'
import { useLiveBriefings } from '@renderer/hooks/useLiveBriefings'
import MiniWidget from './components/MiniWidget'

const DEFAULT_SESSION_ID = 9002

/** HIGH 위험이 도착하면 이 시간 동안만 창을 강조 상태로 둔다 (목업 SC-03 부속). */
const HIGH_EMPHASIS_MS = 15000

/**
 * SC-03 부속: 미니 모드 창의 내용.
 * Electron 메인 프로세스가 항상-위 별도 창(340×220)으로 이 라우트를 로드한다.
 *
 * 데이터가 두 갈래로 온다:
 *  - 브리핑: 콘솔 창이 AI 서버에서 받아 main을 통해 중계한 것을 구독한다
 *    (미니 창이 직접 오디오를 보내면 마이크가 이중으로 열리고 세션이 충돌한다).
 *  - 시청자·여론: 백엔드 스냅샷을 직접 폴링한다. 별도 렌더러라 콘솔의 상태를
 *    공유하지 않고, 세컨드 모니터에 띄워두는 창이라 값이 멈춰 있으면 안 된다.
 *
 * 미리보기: `#/console/mini` (기본) / `#/console/mini?state=high` (HIGH 강조)
 */
export default function MiniModePage(): React.JSX.Element {
  const [params] = useSearchParams()
  const { data } = useLiveSnapshot(DEFAULT_SESSION_ID)
  const liveBriefings = useLiveBriefings()
  /** 강조 시간이 지난 HIGH 브리핑 id — 강조를 끄는 쪽만 상태로 둔다. */
  const [expiredHighId, setExpiredHighId] = useState<number | null>(null)

  const newestHighId = liveBriefings.find(
    (briefing) => briefing.briefingType === 'RISK' && briefing.level === 'HIGH'
  )?.briefingId

  // 새 HIGH가 오면 타이머를 다시 걸고, 시간이 지나면 그 id를 만료 처리해 강조를 끈다.
  useEffect(() => {
    if (newestHighId === undefined) return
    const timer = window.setTimeout(() => setExpiredHighId(newestHighId), HIGH_EMPHASIS_MS)
    return () => window.clearTimeout(timer)
  }, [newestHighId])

  const isHigh =
    params.get('state') === 'high' ||
    (newestHighId !== undefined && newestHighId !== expiredHighId)

  return (
    <div className="h-screen w-screen">
      <MiniWidget
        data={data}
        briefings={[...liveBriefings, ...(data?.briefings ?? [])]}
        isHigh={isHigh}
        onGoConsole={() => window.api.focusMainWindow()}
        onClose={() => window.api.closeMiniWindow()}
      />
    </div>
  )
}
