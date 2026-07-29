import { useState } from 'react'
import { Button } from '@renderer/components/ui/button'
import { Input } from '@renderer/components/ui/input'
import { cn } from '@renderer/lib/utils'
import { useObs } from '@renderer/hooks/useObs'
import type { ObsConnStatus } from '@renderer/types/obs'

const STATUS_LABEL: Record<ObsConnStatus, string> = {
  DISCONNECTED: '미연결',
  CONNECTING: '연결 중',
  CONNECTED: '연결됨'
}

/**
 * 콘솔 상태줄의 OBS 연결·송출 제어.
 * "방송 시작/종료" 버튼이 obs-websocket으로 OBS 송출(StartStream/StopStream)을 제어한다.
 * (LAIVE 분석 세션 시작/종료 = POST /broadcast-sessions 는 백엔드 연동 ③에서 별도 처리)
 */
export default function ObsControl(): React.JSX.Element {
  const obs = useObs()
  const [showForm, setShowForm] = useState(false)
  const [url, setUrl] = useState('ws://127.0.0.1:4455')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const connected = obs.status === 'CONNECTED'

  const handleConnect = async (): Promise<void> => {
    setError(null)
    setBusy(true)
    try {
      await obs.connect(url, password || undefined)
      setShowForm(false)
    } catch (e) {
      setError(e instanceof Error ? e.message : '연결에 실패했어요')
    } finally {
      setBusy(false)
    }
  }

  const handleStreamToggle = async (): Promise<void> => {
    setError(null)
    try {
      if (obs.streaming) await obs.stopStream()
      else await obs.startStream()
    } catch (e) {
      setError(e instanceof Error ? e.message : '요청에 실패했어요')
    }
  }

  return (
    <div className="relative flex items-center gap-2">
      {/* OBS 연결 상태 */}
      <span className="flex items-center gap-1.5 rounded-lg bg-muted px-2.5 py-1.5 text-xs text-muted-foreground">
        <span
          className={cn(
            'size-1.5 rounded-full',
            connected
              ? 'bg-success'
              : obs.status === 'CONNECTING'
                ? 'bg-warning'
                : 'bg-muted-foreground'
          )}
        />
        OBS {STATUS_LABEL[obs.status]}
      </span>

      {connected ? (
        <>
          {/* 방송 시작/종료 = OBS 송출 제어 */}
          <Button
            size="sm"
            variant={obs.streaming ? 'destructive' : 'default'}
            onClick={handleStreamToggle}
          >
            {obs.streaming ? '방송 종료' : '방송 시작'}
          </Button>
          <Button size="sm" variant="ghost" onClick={() => obs.disconnect()}>
            연결 해제
          </Button>
        </>
      ) : (
        <Button size="sm" variant="outline" onClick={() => setShowForm((v) => !v)}>
          OBS 연결
        </Button>
      )}

      {/* 연결 폼 */}
      {showForm && !connected && (
        <div className="absolute top-full right-0 z-50 mt-2 flex w-64 flex-col gap-2 rounded-lg border bg-card p-3 shadow-md">
          <label className="text-xs font-medium text-muted-foreground">WebSocket 주소</label>
          <Input value={url} onChange={(e) => setUrl(e.target.value)} placeholder="ws://127.0.0.1:4455" />
          <label className="text-xs font-medium text-muted-foreground">비밀번호</label>
          <Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
          {error && <p className="text-xs text-destructive">{error}</p>}
          <Button size="sm" onClick={handleConnect} disabled={busy}>
            {busy ? '연결 중…' : '연결'}
          </Button>
          <p className="text-micro leading-relaxed text-muted-foreground">
            OBS · 도구 → WebSocket 서버 설정에서 서버를 활성화해야 해요.
          </p>
        </div>
      )}

      {error && connected && <span className="text-xs text-destructive">{error}</span>}
    </div>
  )
}
