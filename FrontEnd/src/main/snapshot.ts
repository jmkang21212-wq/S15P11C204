import { ipcMain, BrowserWindow } from 'electron'

/**
 * 실시간 콘솔 스냅샷(GET /broadcast-sessions/{id}/live-snapshot)을 창 사이에 릴레이한다.
 *
 * session.ts와 같은 이유다 — 콘솔 창(owner)만 인증된 렌더러라 BE가 설계한 2~3초 폴링을
 * 직접 할 수 있고, 미니·오버레이 창은 별도 BrowserWindow라 토큰이 없다. main은 최신값을
 * 받아 다른 창에 뿌리는 릴레이만 한다 — payload 구조는 렌더러(hooks/useLiveSnapshot.ts,
 * types/console.ts)만 알면 되므로 여기서는 검사하지 않는다.
 */

let lastState: unknown = null

function broadcastState(): void {
  for (const win of BrowserWindow.getAllWindows()) {
    if (!win.isDestroyed()) win.webContents.send('snapshot:state', lastState)
  }
}

export function registerSnapshotIpc(): void {
  /** owner 창(콘솔)이 새 조회 결과를 밀어 넣는다. */
  ipcMain.on('snapshot:state', (_event, snapshot: unknown) => {
    lastState = snapshot
    broadcastState()
  })

  /** follower 창(미니·오버레이)이 열리자마자 초기값을 받는다. */
  ipcMain.handle('snapshot:get-state', () => lastState)
}
