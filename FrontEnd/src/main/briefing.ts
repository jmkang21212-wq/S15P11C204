import { ipcMain, BrowserWindow } from 'electron'

/**
 * 창 간 실시간 브리핑 중계.
 *
 * 오디오 캡처와 AI 서버 연결은 콘솔 창이 단독으로 소유한다 — 마이크를 두 번 열 수 없고,
 * 같은 session_id로 두 번 접속하면 앞 연결이 조용히 죽기 때문이다(AI/FRONTEND_API.md).
 * 그래서 콘솔이 받은 브리핑을 main이 중계해 미니 창에도 같은 내용을 보여준다.
 *
 * main은 페이로드를 해석하지 않는다 — 구조는 렌더러의 Briefing 타입이 정한다.
 */

/** 나중에 열린 창도 직전 브리핑을 볼 수 있도록 최근 것만 보관한다. */
const MAX_BUFFERED = 20
let recentBriefings: unknown[] = []

export function registerBriefingIpc(): void {
  ipcMain.on('briefing:publish', (event, briefing: unknown) => {
    recentBriefings = [briefing, ...recentBriefings].slice(0, MAX_BUFFERED)

    for (const win of BrowserWindow.getAllWindows()) {
      // 보낸 창은 이미 자기 화면에 반영했으므로 제외한다.
      if (win.isDestroyed() || win.webContents.id === event.sender.id) continue
      win.webContents.send('briefing:new', briefing)
    }
  })

  ipcMain.handle('briefing:recent', () => recentBriefings)

  /**
   * 방송이 끝나면 콘솔이 부른다. 버퍼는 "나중에 열린 창이 직전 브리핑을 볼 수 있게" 두는
   * 것이라 방송 경계를 넘으면 의미가 없다 — 비우지 않으면 다음 방송에서 미니 창을 열 때
   * 지난 방송의 위험 발언·약속이 히스토리로 뜬다.
   */
  ipcMain.on('briefing:clear', () => {
    recentBriefings = []
  })
}
