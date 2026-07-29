/** OBS(obs-websocket) 연결/송출 상태 — preload 브릿지와 동일한 형태. */
export type ObsConnStatus = 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED'

export interface ObsState {
  status: ObsConnStatus
  streaming: boolean
}
