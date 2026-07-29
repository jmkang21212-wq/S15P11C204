import { useCallback } from 'react'
import { useAsyncData } from '@renderer/hooks/useAsyncData'
import { getLiveSnapshot } from '@renderer/api/console'

const DEFAULT_SESSION_ID = 9002

/**
 * SC-03 부속: OBS 브라우저 소스용 투명 오버레이 (1920×1080 전제).
 * 시청자 노출용 — 호응도 게이지(FR-F-05)만 표시하고 스트리머 전용 정보는 절대 넣지 않는다.
 *
 * TODO(연동): OBS가 이 URL을 브라우저 소스로 로드. body 투명 배경 설정 필요.
 *   호응도 값은 실제로는 WebSocket으로 실시간 갱신.
 */
export default function OverlayPage(): React.JSX.Element {
  const loader = useCallback(() => getLiveSnapshot(DEFAULT_SESSION_ID), [])
  const { data } = useAsyncData(loader)
  const engagement = data?.engagement ?? 0

  return (
    <div data-surface="overlay" className="relative h-screen w-screen bg-transparent">
      {/* 호응도 게이지 — 우하단 고정 */}
      <div className="absolute right-6 bottom-6 flex items-center gap-3 rounded-2xl border border-white/15 bg-overlay-backdrop/62 px-4 py-2.5 backdrop-blur">
        <span className="text-2xl" style={{ filter: 'drop-shadow(0 0 6px rgba(251,146,60,.8))' }}>
          🔥
        </span>
        <div>
          <div className="mb-1 text-micro font-semibold tracking-wider text-white/65">호응도</div>
          <div className="h-2.5 w-[118px] overflow-hidden rounded-full bg-white/15">
            <div
              className="h-full rounded-full bg-gradient-to-r from-gauge-from to-gauge-to"
              style={{ width: `${engagement}%` }}
            />
          </div>
        </div>
        <span className="font-mono text-base font-bold text-white">{engagement}</span>
      </div>
    </div>
  )
}
