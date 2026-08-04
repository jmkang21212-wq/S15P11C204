/** OBS(obs-websocket) 연결/송출 상태 — preload 브릿지와 동일한 형태. */
export type ObsConnStatus = 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED'

export interface ObsState {
  status: ObsConnStatus
  streaming: boolean
  recording: boolean
}

/** GetRecordStatus 응답 중 클립 계산에 쓰는 필드만. outputDuration은 밀리초 단위다. */
export interface ObsRecordStatus {
  outputActive: boolean
  outputDuration: number
}
