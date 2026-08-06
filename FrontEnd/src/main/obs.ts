import { app, ipcMain, BrowserWindow, safeStorage } from 'electron'
import { join } from 'path'
import { readFileSync, writeFileSync } from 'fs'
// default import는 CJS 상호운용에서 생성자로 안 잡히는 경우가 있어 named export를 쓴다.
import { OBSWebSocket } from 'obs-websocket-js'

/**
 * OBS(obs-websocket 5.x) 연결·제어를 main 프로세스에서 관리한다.
 * 렌더러는 CSP·보안상 직접 소켓을 열지 않고 IPC로 이 모듈에 요청한다.
 * OBS 미실행/미연결 시에도 앱이 죽지 않도록 모든 실패를 graceful하게 처리한다.
 *
 * 접속 정보도 여기서 보관한다 — 소켓 소유자가 자격 증명도 함께 갖는 편이 응집도가 높고,
 * 비밀번호를 렌더러(localStorage)에 두지 않을 수 있다. authStore가 Access Token을
 * 메모리에만 두는 것과 같은 이유로, 평문 비밀번호를 디스크에 남기지 않는다.
 */

type ObsConnStatus = 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED'

interface ObsState {
  status: ObsConnStatus
  streaming: boolean
  recording: boolean
}

/** 렌더러에 돌려주는 복원된 접속 정보. */
interface ObsCredentials {
  url: string
  password: string
}

/** 디스크에 저장되는 형태. 비밀번호는 OS 암호화를 거친 base64로만 저장한다. */
interface StoredCredentials {
  url: string
  passwordEnc?: string
}

function credentialsPath(): string {
  return join(app.getPath('userData'), 'obs-credentials.json')
}

/** 저장된 접속 정보를 읽는다. 없거나 읽을 수 없으면 null — 사용자가 다시 입력하면 된다. */
function loadCredentials(): ObsCredentials | null {
  try {
    const stored = JSON.parse(readFileSync(credentialsPath(), 'utf-8')) as StoredCredentials
    if (typeof stored?.url !== 'string') return null
    let password = ''
    if (stored.passwordEnc && safeStorage.isEncryptionAvailable()) {
      password = safeStorage.decryptString(Buffer.from(stored.passwordEnc, 'base64'))
    }
    return { url: stored.url, password }
  } catch {
    // 파일 없음 · JSON 깨짐 · 복호화 실패(다른 사용자 계정으로 복사된 경우 등)
    return null
  }
}

/**
 * 연결에 성공한 접속 정보를 저장한다.
 *
 * safeStorage를 쓸 수 없는 환경(libsecret이 없는 일부 Linux)에서는 **비밀번호를 저장하지
 * 않고 주소만** 남긴다. 편의를 위해 평문 비밀번호를 디스크에 흘리지는 않는다.
 */
function saveCredentials(url: string, password: string): void {
  const stored: StoredCredentials = { url }
  if (password && safeStorage.isEncryptionAvailable()) {
    stored.passwordEnc = safeStorage.encryptString(password).toString('base64')
  }
  try {
    writeFileSync(credentialsPath(), JSON.stringify(stored), 'utf-8')
  } catch {
    // 저장 실패는 다음 실행에 다시 입력하면 그만이다 — 연결 자체를 막지 않는다.
  }
}

let obs: OBSWebSocket | null = null
const state: ObsState = { status: 'DISCONNECTED', streaming: false, recording: false }

/** 현재 상태를 열린 모든 창(메인·미니)에 브로드캐스트한다. */
function broadcastState(): void {
  for (const win of BrowserWindow.getAllWindows()) {
    if (!win.isDestroyed()) win.webContents.send('obs:state', { ...state })
  }
}

function setStatus(status: ObsConnStatus): void {
  state.status = status
  if (status !== 'CONNECTED') {
    state.streaming = false
    state.recording = false
  }
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
  // 콘솔의 "방송 종료" 버튼 외에 OBS 쪽에서 직접 녹화를 멈춘 경우(단축키·UI)의 보조 경로.
  // outputPath는 여기서도 오지만, 종료 흐름은 명세대로 StopRecord의 반환값을 정본으로 쓴다.
  client.on('RecordStateChanged', (data) => {
    state.recording = data.outputActive
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
        const streamStatus = await client.call('GetStreamStatus')
        state.streaming = streamStatus.outputActive
        // 콘솔을 재시작한 사이 OBS가 이미 녹화 중이었을 수 있다(예: 앱 크래시 후 재실행).
        // 실제 상태를 반영해야 "방송 시작"이 중복으로 StartRecord를 부르지 않는다.
        const recordStatus = await client.call('GetRecordStatus')
        state.recording = recordStatus.outputActive
        setStatus('CONNECTED')
        // 연결에 성공한 값만 저장한다 — 틀린 정보를 저장해 두면 다음 실행의 자동 연결이
        // 매번 실패하고, 사용자는 왜 안 되는지 알기 어렵다.
        saveCredentials(url, password ?? '')
        return { ...state }
      } catch (e) {
        // OBS 미실행·WebSocket 서버 꺼짐·비밀번호 오류 등 → graceful
        setStatus('DISCONNECTED')
        // ECONNREFUSED: OBS가 아예 꺼져 있거나 WebSocket 서버가 비활성화된 경우.
        // Node의 원본 메시지("connect ECONNREFUSED 127.0.0.1:4455")는 사용자에게 그대로
        // 보여주기엔 낯설어서, 여기서 뜻이 통하는 문구로 바꿔 던진다.
        const code = (e as { code?: string })?.code
        const message = e instanceof Error ? e.message : ''
        if (code === 'ECONNREFUSED' || message.includes('ECONNREFUSED')) {
          throw new Error('OBS가 실행되어 있지 않아요. OBS를 켜고 WebSocket 서버를 확인해 주세요.')
        }
        throw new Error(e instanceof Error ? e.message : 'OBS 연결에 실패했어요')
      }
    }
  )

  ipcMain.handle('obs:disconnect', async () => {
    if (obs) await obs.disconnect().catch(() => undefined)
    setStatus('DISCONNECTED')
  })

  ipcMain.handle('obs:get-state', () => ({ ...state }))

  ipcMain.handle('obs:get-credentials', () => loadCredentials())

  ipcMain.handle('obs:start-stream', async () => {
    if (!obs || state.status !== 'CONNECTED') throw new Error('OBS가 연결되어 있지 않아요')
    await obs.call('StartStream')
  })

  ipcMain.handle('obs:stop-stream', async () => {
    if (!obs || state.status !== 'CONNECTED') throw new Error('OBS가 연결되어 있지 않아요')
    await obs.call('StopStream')
  })

  ipcMain.handle('obs:start-record', async () => {
    if (!obs || state.status !== 'CONNECTED') throw new Error('OBS가 연결되어 있지 않아요')
    await obs.call('StartRecord')
    state.recording = true
    broadcastState()
  })

  /**
   * 녹화를 멈추고 결과 파일 경로를 반환한다.
   * outputPath는 StopRecord 응답 형태 그대로다(obs-websocket-js 타입 정의로 확인 완료) —
   * 서버가 받는 필드명(outputPath)과 이름이 같아 변환 없이 그대로 넘기면 된다.
   */
  ipcMain.handle('obs:stop-record', async () => {
    if (!obs || state.status !== 'CONNECTED') throw new Error('OBS가 연결되어 있지 않아요')
    const result = await obs.call('StopRecord')
    state.recording = false
    broadcastState()
    return { outputPath: result.outputPath }
  })

  /**
   * outputDuration은 밀리초 단위다 — 서버의 baseTimecodeMs와 단위가 일치해 변환이 필요 없다
   * (obs-websocket-js 타입 정의로 확인 완료).
   */
  ipcMain.handle('obs:get-record-status', async () => {
    if (!obs || state.status !== 'CONNECTED') throw new Error('OBS가 연결되어 있지 않아요')
    const result = await obs.call('GetRecordStatus')
    return { outputActive: result.outputActive, outputDuration: result.outputDuration }
  })
}
