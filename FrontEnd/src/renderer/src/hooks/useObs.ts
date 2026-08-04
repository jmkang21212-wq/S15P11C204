import { useCallback, useEffect, useState } from 'react'
import type { ObsRecordStatus, ObsState } from '@renderer/types/obs'

/**
 * OBS 연결/송출/녹화 상태 훅.
 * 실제 소켓은 main이 소유하고, 여기서는 IPC 브릿지(window.api.obs)로 상태를 구독·제어한다.
 */
export function useObs(): ObsState & {
  connect: (url: string, password?: string) => Promise<ObsState>
  disconnect: () => Promise<void>
  startStream: () => Promise<void>
  stopStream: () => Promise<void>
  startRecord: () => Promise<void>
  /** 녹화를 멈추고 결과 파일 경로를 반환한다. */
  stopRecord: () => Promise<{ outputPath: string }>
  getRecordStatus: () => Promise<ObsRecordStatus>
} {
  const [state, setState] = useState<ObsState>({
    status: 'DISCONNECTED',
    streaming: false,
    recording: false
  })

  useEffect(() => {
    let active = true
    window.api.obs.getState().then((s) => {
      if (active) setState(s)
    })
    const off = window.api.obs.onState((s) => setState(s))
    return () => {
      active = false
      off()
    }
  }, [])

  const connect = useCallback(
    (url: string, password?: string) => window.api.obs.connect(url, password),
    []
  )
  const disconnect = useCallback(() => window.api.obs.disconnect(), [])
  const startStream = useCallback(() => window.api.obs.startStream(), [])
  const stopStream = useCallback(() => window.api.obs.stopStream(), [])
  const startRecord = useCallback(() => window.api.obs.startRecord(), [])
  const stopRecord = useCallback(() => window.api.obs.stopRecord(), [])
  const getRecordStatus = useCallback(() => window.api.obs.getRecordStatus(), [])

  return {
    ...state,
    connect,
    disconnect,
    startStream,
    stopStream,
    startRecord,
    stopRecord,
    getRecordStatus
  }
}
