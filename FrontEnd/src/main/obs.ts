import { ipcMain, BrowserWindow } from 'electron'
// default import는 CJS 상호운용에서 생성자로 안 잡히는 경우가 있어 named export를 쓴다.
import { OBSWebSocket } from 'obs-websocket-js'

/**
 * OBS(obs-websocket 5.x) 연결·제어를 main 프로세스에서 관리한다.
 * 렌더러는 CSP·보안상 직접 소켓을 열지 않고 IPC로 이 모듈에 요청한다.
 * OBS 미실행/미연결 시에도 앱이 죽지 않도록 모든 실패를 graceful하게 처리한다.
 */

type ObsConnStatus = 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED'

interface ObsState {
  status: ObsConnStatus
  streaming: boolean
}

let obs: OBSWebSocket | null = null
const state: ObsState = { status: 'DISCONNECTED', streaming: false }

/** 현재 상태를 열린 모든 창(메인·미니)에 브로드캐스트한다. */
function broadcastState(): void {
  for (const win of BrowserWindow.getAllWindows()) {
    if (!win.isDestroyed()) win.webContents.send('obs:state', { ...state })
  }
}

function setStatus(status: ObsConnStatus): void {
  state.status = status
  if (status !== 'CONNECTED') state.streaming = false
  broadcastState()
}

/** 클라이언트를 만들고 상태 이벤트 구독을 1회만 건다. */
function ensureClient(): OBSWebSocket {
  if (obs) return obs
  const client = new OBSWebSocket()
  client.on('ConnectionClosed', () => setStatus('DISCONNECTED'))
  client.on('StreamStateChanged', (data) => {
    state.streaming = data.outputActive
    broadcastState()
  })
  obs = client
  return client
}

export function registerObsIpc(): void {
  ipcMain.handle(
    'obs:connect',
    async (_e, { url, password }: { url: string; password?: string }) => {
      const client = ensureClient()
      setStatus('CONNECTING')
      try {
        await client.connect(url, password || undefined)
        const status = await client.call('GetStreamStatus')
        state.streaming = status.outputActive
        setStatus('CONNECTED')
        return { ...state }
      } catch (e) {
        // OBS 미실행·WebSocket 서버 꺼짐·비밀번호 오류 등 → graceful
        setStatus('DISCONNECTED')
        throw new Error(e instanceof Error ? e.message : 'OBS 연결에 실패했어요')
      }
    }
  )

  ipcMain.handle('obs:disconnect', async () => {
    if (obs) await obs.disconnect().catch(() => undefined)
    setStatus('DISCONNECTED')
  })

  ipcMain.handle('obs:get-state', () => ({ ...state }))

  ipcMain.handle('obs:start-stream', async () => {
    if (!obs || state.status !== 'CONNECTED') throw new Error('OBS가 연결되어 있지 않아요')
    await obs.call('StartStream')
  })

  ipcMain.handle('obs:stop-stream', async () => {
    if (!obs || state.status !== 'CONNECTED') throw new Error('OBS가 연결되어 있지 않아요')
    await obs.call('StopStream')
  })
}
