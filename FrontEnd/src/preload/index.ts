import { contextBridge, ipcRenderer } from 'electron'
import { electronAPI } from '@electron-toolkit/preload'

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
  focusMainWindow: (): void => ipcRenderer.send('main:focus')
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
