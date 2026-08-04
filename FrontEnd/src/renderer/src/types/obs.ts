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

/**
 * OBS에 설정된 송출 대상. 스트림 키는 main이 걸러내므로 여기 없다.
 * `rtmp_common`이면 service에 서비스 이름이, `rtmp_custom`이면 server에 RTMP 주소가 온다.
 */
export interface ObsStreamService {
  streamServiceType: string
  service: string | null
  server: string | null
}
