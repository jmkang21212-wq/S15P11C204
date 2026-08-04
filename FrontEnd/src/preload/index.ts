import { contextBridge, ipcRenderer, type IpcRendererEvent } from 'electron'
import { electronAPI } from '@electron-toolkit/preload'

type ObsState = {
  status: 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED'
  streaming: boolean
  recording: boolean
}
type ObsRecordStatus = { outputActive: boolean; outputDuration: number }
type ClipCutRequest = { recordingPath: string; clipStartSec: number; clipDurationSec: number }
type ClipCutResult =
  | { ok: true; clip: Uint8Array; thumbnail: Uint8Array }
  | { ok: false; permanent: boolean; reason: string }

// Custom APIs for renderer
const api = {
  /** 기본 브라우저로 URL을 연다. main에서 http/https만 허용한다. */
  openExternal: (url: string): Promise<void> => ipcRenderer.invoke('app:open-external', url),
  /** 파일을 내려받는다. Electron 기본 저장 다이얼로그가 뜬다. */
  downloadFile: (url: string): Promise<void> => ipcRenderer.invoke('app:download-file', url),
  /** 미니 모드 창을 연다 (이미 있으면 앞으로 가져온다). */
  openMiniWindow: (): void => ipcRenderer.send('mini:open'),
  /** 미니 모드 창을 닫는다. */
  closeMiniWindow: (): void => ipcRenderer.send('mini:close'),
  /** 메인 창을 앞으로 가져온다 (미니 창의 "콘솔로"에서 사용). */
  focusMainWindow: (): void => ipcRenderer.send('main:focus'),
  /** OBS(obs-websocket) 연결·제어 — 실제 소켓은 main이 소유한다. */
  obs: {
    connect: (url: string, password?: string): Promise<ObsState> =>
      ipcRenderer.invoke('obs:connect', { url, password }),
    disconnect: (): Promise<void> => ipcRenderer.invoke('obs:disconnect'),
    getState: (): Promise<ObsState> => ipcRenderer.invoke('obs:get-state'),
    /**
     * 지난번 연결에 성공한 접속 정보. 없으면 null.
     * 비밀번호는 main이 OS 암호화(safeStorage)로 보관한다 — 렌더러는 저장에 관여하지 않는다.
     */
    getCredentials: (): Promise<{ url: string; password: string } | null> =>
      ipcRenderer.invoke('obs:get-credentials'),
    startStream: (): Promise<void> => ipcRenderer.invoke('obs:start-stream'),
    stopStream: (): Promise<void> => ipcRenderer.invoke('obs:stop-stream'),
    startRecord: (): Promise<void> => ipcRenderer.invoke('obs:start-record'),
    /** 녹화를 멈추고 결과 파일 경로를 반환한다. */
    stopRecord: (): Promise<{ outputPath: string }> => ipcRenderer.invoke('obs:stop-record'),
    /** outputDuration은 밀리초 — 서버의 baseTimecodeMs와 단위가 같다. */
    getRecordStatus: (): Promise<ObsRecordStatus> => ipcRenderer.invoke('obs:get-record-status'),
    /** 상태 변경 구독. 반환 함수를 호출하면 구독 해제. */
    onState: (callback: (state: ObsState) => void): (() => void) => {
      const listener = (_e: IpcRendererEvent, state: ObsState): void => callback(state)
      ipcRenderer.on('obs:state', listener)
      return () => ipcRenderer.removeListener('obs:state', listener)
    }
  },
  /** OAuth 인앱 창을 열고 returnUri로 복귀하면 그 URL을, 취소 시 null을 돌려준다. */
  oauth: {
    open: (startUrl: string, returnUriPrefix: string): Promise<string | null> =>
      ipcRenderer.invoke('oauth:open', { startUrl, returnUriPrefix })
  },
  /** 실시간 브리핑 중계 — 콘솔이 받은 경고·약속을 미니 창에도 보여준다. */
  briefing: {
    publish: (briefing: unknown): void => ipcRenderer.send('briefing:publish', briefing),
    recent: (): Promise<unknown[]> => ipcRenderer.invoke('briefing:recent'),
    clear: (): void => ipcRenderer.send('briefing:clear'),
    /** 중계된 브리핑 구독. 반환 함수를 호출하면 구독 해제. */
    onNew: (callback: (briefing: unknown) => void): (() => void) => {
      const listener = (_e: IpcRendererEvent, briefing: unknown): void => callback(briefing)
      ipcRenderer.on('briefing:new', listener)
      return () => ipcRenderer.removeListener('briefing:new', listener)
    }
  },
  /**
   * 진행 중 방송 세션 상태 릴레이. main은 값을 검사하지 않고 그대로 전달하므로
   * 여기서는 unknown으로 받고, 실제 타입은 preload/index.d.ts의 SessionBridge가 정의한다.
   */
  session: {
    publish: (session: unknown): void => ipcRenderer.send('session:state', session),
    getState: (): Promise<unknown> => ipcRenderer.invoke('session:get-state'),
    onState: (callback: (session: unknown) => void): (() => void) => {
      const listener = (_e: IpcRendererEvent, session: unknown): void => callback(session)
      ipcRenderer.on('session:state', listener)
      return () => ipcRenderer.removeListener('session:state', listener)
    }
  },
  /**
   * 실시간 콘솔 스냅샷 릴레이. main은 값을 검사하지 않고 그대로 전달하므로
   * 여기서는 unknown으로 받고, 실제 타입은 preload/index.d.ts의 SnapshotBridge가 정의한다.
   */
  snapshot: {
    publish: (snapshot: unknown): void => ipcRenderer.send('snapshot:state', snapshot),
    getState: (): Promise<unknown> => ipcRenderer.invoke('snapshot:get-state'),
    onState: (callback: (snapshot: unknown) => void): (() => void) => {
      const listener = (_e: IpcRendererEvent, snapshot: unknown): void => callback(snapshot)
      ipcRenderer.on('snapshot:state', listener)
      return () => ipcRenderer.removeListener('snapshot:state', listener)
    }
  },
  /**
   * 반복 질문 답변 완료 요청 — 미니 창은 토큰이 없어 API를 직접 부를 수 없으므로
   * 인증을 가진 콘솔 창이 대신 호출하도록 요청만 넘긴다 (릴레이 방향이 반대다).
   */
  question: {
    requestDismiss: (sessionId: number, questionId: number): void =>
      ipcRenderer.send('question:dismiss-request', { sessionId, questionId }),
    onDismissRequest: (callback: (payload: unknown) => void): (() => void) => {
      const listener = (_e: IpcRendererEvent, payload: unknown): void => callback(payload)
      ipcRenderer.on('question:dismiss-request', listener)
      return () => ipcRenderer.removeListener('question:dismiss-request', listener)
    }
  },
  /** 하이라이트 클립 컷 — ffmpeg 실행은 main이 하고, 업로드(HTTP)는 렌더러가 한다. */
  clip: {
    cut: (request: ClipCutRequest): Promise<ClipCutResult> =>
      ipcRenderer.invoke('clip:cut', request)
  }
}

// Use `contextBridge` APIs to expose Electron APIs to
// renderer only if context isolation is enabled, otherwise
// just add to the DOM global.
if (process.contextIsolated) {
  try {
    contextBridge.exposeInMainWorld('electron', electronAPI)
    contextBridge.exposeInMainWorld('api', api)
  } catch (error) {
    console.error(error)
  }
} else {
  // @ts-ignore (define in dts)
  window.electron = electronAPI
  // @ts-ignore (define in dts)
  window.api = api
}
