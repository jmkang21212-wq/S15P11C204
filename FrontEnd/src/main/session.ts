import { ipcMain, BrowserWindow } from 'electron'
import { setBroadcastPowerGuard } from './power'

/**
 * 진행 중인 방송 세션 상태를 창 사이에 릴레이한다.
 *
 * obs.ts와 달리 main이 이 상태의 소유자가 아니다 — 소유자는 백엔드고, 메인 창(owner)만
 * 인증된 렌더러라 GET /broadcast-sessions/current를 직접 폴링할 수 있다. 미니·오버레이
 * 창은 별도 BrowserWindow라 authStore(메모리 전용)를 공유하지 못하고, Refresh Token이
 * 1회용으로 회전해 여러 창이 동시에 401→refresh를 시도하면 서로를 로그아웃시킨다.
 * 그래서 main은 여기서 "최신값을 받아 다른 창에 뿌리는 릴레이" 역할만 한다.
 *
 * payload 형태는 렌더러(hooks/useCurrentSession.ts, types/broadcastSession.ts)만 알면 되므로
 * 여기서는 구조를 검사하지 않고 그대로 전달한다.
 */

let lastState: unknown = null

function broadcastState(): void {
  for (const win of BrowserWindow.getAllWindows()) {
    if (!win.isDestroyed()) win.webContents.send('session:state', lastState)
  }
}

export function registerSessionIpc(): void {
  /** owner 창(메인)이 새 조회 결과를 밀어 넣는다. */
  ipcMain.on('session:state', (_event, session: unknown) => {
    lastState = session
    // 진행 중인 세션이 없으면 null이 온다. main이 "방송 중인가"를 알 수 있는 신호가
    // 이미 여기로 흐르고 있어, 별도 IPC 없이 전력·스로틀 보호를 이 지점에 붙인다.
    // payload 구조는 여전히 들여다보지 않는다(위 주석 참고).
    setBroadcastPowerGuard(session !== null)
    broadcastState()
  })

  /** follower 창(미니·오버레이)이 열리자마자 초기값을 받는다. */
  ipcMain.handle('session:get-state', () => lastState)
}
