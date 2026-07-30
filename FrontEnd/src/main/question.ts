import { ipcMain, BrowserWindow } from 'electron'

/**
 * 반복 질문 "답변 완료"(dismiss) 요청을 미니 창 → 콘솔 창으로 릴레이한다.
 *
 * snapshot.ts·session.ts와 방향만 반대다. 미니 창은 별도 BrowserWindow라 토큰이 없어
 * API를 직접 부를 수 없으므로, 인증을 가진 콘솔 창(owner)이 대신 호출하도록 요청만 넘긴다.
 * payload 구조는 렌더러만 알면 되므로 여기서는 검사하지 않는다.
 */
export function registerQuestionIpc(): void {
  ipcMain.on('question:dismiss-request', (event, payload: unknown) => {
    for (const win of BrowserWindow.getAllWindows()) {
      // 요청을 보낸 창(미니)에는 되돌리지 않는다 — 그 창은 이미 화면에서 숨겼다.
      if (win.isDestroyed() || win.webContents.id === event.sender.id) continue
      win.webContents.send('question:dismiss-request', payload)
    }
  })
}
