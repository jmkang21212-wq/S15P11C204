import { BrowserWindow, powerSaveBlocker } from 'electron'

/**
 * 방송 중에만 켜는 "갱신이 멈추지 않게" 보호 두 가지.
 *
 * 콘솔은 세컨드 모니터에 띄워 두고 쓰는 화면이라(design.md SC-03) 방송 내내 포커스를
 * 잃은 채 다른 창에 가려져 있기 쉽다. 그 상태에서 타이머가 묶이면 시청자 수·여론이 과거
 * 값으로 굳고, 60초마다 보내는 녹화 기준점(useRecordingReporter)이 밀려 클립 컷 위치
 * 오차가 그대로 쌓인다.
 *
 *  1. backgroundThrottling — Chromium이 가려짐·최소화된 페이지의 타이머를 1분 1회로
 *     묶고 5분 뒤 더 조이는 동작을 끈다. 스냅샷 3초 폴링이 여기 걸린다.
 *  2. powerSaveBlocker — 유휴 절전으로 프로세스가 통째로 멈추는 것을 막는다.
 *     사용자가 직접 재우는 절전(덮개 닫기 등)까지 막지는 못한다.
 *
 * 둘 다 방송 중에만 켠다. 앱만 띄워 둔 상태에서도 계속 켜 두면 CPU와 배터리를 그냥
 * 태우는데, 대시보드처럼 실시간성이 낮은 화면은 그럴 이유가 없다.
 *
 * 이것들로도 못 막는 경우가 남는다 — 사용자가 직접 재운 절전, 최대 절전, 렌더러 크래시.
 * 그래서 렌더러 쪽에도 복귀 시 즉시 재조회하는 안전망을 따로 뒀다
 * (useLiveSnapshot·useRecordingReporter·useCurrentSession의 focus 핸들러).
 */

let active = false
let blockerId: number | null = null

/**
 * 새로 열린 창에 현재 상태를 적용한다. 미니 창은 방송 중에 열리므로 창 생성 시점에
 * 한 번 더 맞춰 주지 않으면 그 창만 기본값(스로틀 켜짐)으로 남는다.
 */
export function applyPowerGuard(win: BrowserWindow): void {
  win.webContents.setBackgroundThrottling(!active)
}

/** 진행 중인 방송 세션의 유무에 맞춰 보호를 켜고 끈다. */
export function setBroadcastPowerGuard(next: boolean): void {
  if (next === active) return
  active = next

  for (const win of BrowserWindow.getAllWindows()) {
    if (!win.isDestroyed()) applyPowerGuard(win)
  }

  if (active) {
    blockerId = powerSaveBlocker.start('prevent-app-suspension')
  } else if (blockerId !== null) {
    powerSaveBlocker.stop(blockerId)
    blockerId = null
  }
}
