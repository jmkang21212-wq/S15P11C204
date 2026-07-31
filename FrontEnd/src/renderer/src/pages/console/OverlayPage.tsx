/**
 * SC-03 부속: OBS 브라우저 소스용 투명 오버레이 (1920×1080 전제).
 * 시청자 노출용 — 호응도 게이지(FR-F-05)만 표시하고 스트리머 전용 정보는 절대 넣지 않는다.
 *
 * 현재 보류 상태다 — 호응도는 스트리머만 확인하기로 정해(2026-07-29) 시청자 노출을
 * 진행하지 않는다. 타입 정합성만 유지한다.
 *
 * TODO(되살릴 때): OBS 브라우저 소스는 앱 밖의 CEF라 Electron preload(contextBridge)도,
 *   JWT·쿠키도 없다 — 이 창이 미니 모드처럼 main의 IPC 릴레이를 구독하는 것도,
 *   live-snapshot을 직접 호출하는 것도 불가능하다. 토큰 기반 공개 엔드포인트가
 *   백엔드에 필요하다.
 */
export default function OverlayPage(): React.JSX.Element {
  const engagement = 0

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
