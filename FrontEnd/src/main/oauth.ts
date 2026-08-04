import { ipcMain, BrowserWindow } from 'electron'

/**
 * OAuth 인앱 창.
 * 렌더러가 준 authorize URL을 별도 창에서 열고, 백엔드가 returnUri로 리다이렉트하면
 * 그 URL을 감지해 창을 닫고 결과를 돌려준다.
 *
 * 기본 세션(defaultSession)을 그대로 써서, 인증 중 백엔드가 심은 Refresh 쿠키가
 * 렌더러의 fetch(credentials:'include')에서도 전송되도록 한다. (별도 partition을 주지 않는다)
 *
 * 로그인·플랫폼 연동이 동일한 흐름이라 공용으로 쓴다.
 */
export function registerOAuthIpc(): void {
  ipcMain.handle(
    'oauth:open',
    (_event, { startUrl, returnUriPrefix }: { startUrl: string; returnUriPrefix: string }) => {
      return new Promise<string | null>((resolve) => {
        const win = new BrowserWindow({
          width: 520,
          height: 720,
          show: false,
          autoHideMenuBar: true,
          webPreferences: { sandbox: true }
        })

        let settled = false
        const finish = (result: string | null): void => {
          if (settled) return
          settled = true
          resolve(result)
          if (!win.isDestroyed()) win.close()
        }

        const check = (url: string): void => {
          if (url.startsWith(returnUriPrefix)) finish(url)
        }

        win.webContents.on('will-redirect', (_e, url) => check(url))
        win.webContents.on('did-navigate', (_e, url) => check(url))
        // 사용자가 인증을 마치지 않고 창을 닫으면 null
        win.on('closed', () => {
          if (!settled) {
            settled = true
            resolve(null)
          }
        })

        win.once('ready-to-show', () => win.show())
        void win.loadURL(startUrl)
      })
    }
  )
}
