import { ElectronAPI } from '@electron-toolkit/preload'

export type ObsConnStatus = 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED'
export interface ObsState {
  status: ObsConnStatus
  streaming: boolean
}

export interface ObsBridge {
  connect: (url: string, password?: string) => Promise<ObsState>
  disconnect: () => Promise<void>
  getState: () => Promise<ObsState>
  startStream: () => Promise<void>
  stopStream: () => Promise<void>
  /** 상태 변경 구독. 반환 함수 호출로 구독 해제. */
  onState: (callback: (state: ObsState) => void) => () => void
}

/**
 * 렌더러에 노출하는 앱 전용 브릿지.
 * 화면별로 늘리지 말고 공통 동작만 여기에 둔다.
 */
export interface LaiveApi {
  /** 기본 브라우저로 URL을 연다 (http/https만 허용). */
  openExternal: (url: string) => Promise<void>
  /** 파일을 내려받는다. 저장 위치는 Electron 기본 다이얼로그로 고른다. */
  downloadFile: (url: string) => Promise<void>
  /** 미니 모드 창을 연다 (이미 있으면 앞으로 가져온다). */
  openMiniWindow: () => void
  /** 미니 모드 창을 닫는다. */
  closeMiniWindow: () => void
  /** 메인 창을 앞으로 가져온다. */
  focusMainWindow: () => void
  /** OBS(obs-websocket) 연결·제어. */
  obs: ObsBridge
}

declare global {
  interface Window {
    electron: ElectronAPI
    api: LaiveApi
  }
}
