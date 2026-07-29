import { contextBridge, ipcRenderer, type IpcRendererEvent } from 'electron'
import { electronAPI } from '@electron-toolkit/preload'

type ObsState = { status: 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED'; streaming: boolean }

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
    startStream: (): Promise<void> => ipcRenderer.invoke('obs:start-stream'),
    stopStream: (): Promise<void> => ipcRenderer.invoke('obs:stop-stream'),
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
    /** 중계된 브리핑 구독. 반환 함수를 호출하면 구독 해제. */
    onNew: (callback: (briefing: unknown) => void): (() => void) => {
      const listener = (_e: IpcRendererEvent, briefing: unknown): void => callback(briefing)
      ipcRenderer.on('briefing:new', listener)
      return () => ipcRenderer.removeListener('briefing:new', listener)
    }
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
