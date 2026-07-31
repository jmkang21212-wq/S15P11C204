import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Button } from '@renderer/components/ui/button'
import { Input } from '@renderer/components/ui/input'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger
} from '@renderer/components/ui/alert-dialog'
import { cn } from '@renderer/lib/utils'
import { useObs } from '@renderer/hooks/useObs'
import { usePrimaryPlatformAccount } from '@renderer/hooks/usePlatformAccounts'
import { ApiError } from '@renderer/api/client'
import { endSession, fetchCurrentSession, startSession } from '@renderer/api/broadcastSessions'
import { getDashboard } from '@renderer/api/dashboard'
import { finishRecording, startRecording } from '@renderer/api/recording'
import { PATHS } from '@renderer/routes/paths'
import type { ObsConnStatus } from '@renderer/types/obs'

/** 녹화가 돌고 있었는데 결과 경로를 못 얻었을 때 FAILED 보고에 채우는 자리표시자.
 * FinishRecordingRequest.outputPath는 @NotBlank라 빈 문자열을 보낼 수 없고, FAILED
 * 보고는 서버가 클립 작업을 만들지 않아 이 값이 실제로 쓰이지 않는다. */
const NO_OUTPUT_PATH = '(recording failed - no output path)'

const STATUS_LABEL: Record<ObsConnStatus, string> = {
  DISCONNECTED: '미연결',
  CONNECTING: '연결 중',
  CONNECTED: '연결됨'
}

/** BE 정규식(`^[a-z]{2}(-[A-Z]{2})?$`)을 만족하는 기본값. TODO: 설정 화면 도입 시 사용자 값으로. */
const DEFAULT_LANGUAGE = 'ko'

/** BE `@Size(max = 200)`과 맞춘다. */
const TITLE_MAX = 200

/**
 * 저장된 접속 정보로 자동 연결을 시도했는지. 앱 실행당 1회로 제한한다.
 *
 * 컴포넌트 상태가 아니라 모듈 레벨인 이유는 사용자가 "연결 해제"를 눌렀을 때다.
 * 콘솔을 다시 열 때마다 자동으로 붙으면 해제할 방법이 없어진다.
 */
let autoConnectTried = false

interface BroadcastControlProps {
  /** 진행 중 세션. null이면 아직 시작 전(준비 모드)이다. */
  sessionId: number | null
  /**
   * 방송 시작에 성공했을 때 호출한다. 라우팅과 음성 분석 시작은 호출부가 맡는다 —
   * `useAudioStream`의 start()는 호출 시점의 sessionId를 캡처하므로, 세션이
   * 확정되어 화면에 반영된 뒤에 불러야 전사·경고가 DB에 저장된다.
   */
  onStarted: (sessionId: number) => void
  /** 방송 종료 시 음성 분석도 함께 멈춘다. */
  onStop: () => void
}

/**
 * 콘솔의 방송 시작·종료 통합 컨트롤.
 *
 * 예전에는 OBS 송출(ObsControl)과 LAIVE 분석 세션(SessionControl)이 별도 버튼이었다.
 * 두 개를 합친 이유는 하나를 끄고 다른 하나를 켜 둔 어중간한 상태가 실제로 문제를 만들어서다 —
 * 특히 송출만 멈추고 분석을 안 끝내면 녹화 종료 보고가 나가지 않아 하이라이트 클립이
 * 영영 만들어지지 않는다. 이제 방송의 시작과 끝은 이 컴포넌트 하나가 책임진다.
 *
 * 3단계로 나뉜다:
 *   OBS 미연결        → [OBS 연결]   (연결 폼 팝오버)
 *   OBS 연결 + 세션 없음 → [방송 시작] (제목 확인 팝오버)
 *   세션 진행 중        → [방송 종료] (확인 다이얼로그)
 *
 * 종료는 OBS 단계가 어떻게 실패하든 `endSession`까지 반드시 진행한다.
 * OBS 사고 때문에 분석 세션이 LIVE로 남아버리면 사용자가 손쓸 방법이 없기 때문이다.
 */
export default function BroadcastControl({
  sessionId,
  onStarted,
  onStop
}: BroadcastControlProps): React.JSX.Element {
  const navigate = useNavigate()
  const obs = useObs()
  const { account: platformAccount, loading: platformAccountLoading } = usePrimaryPlatformAccount()

  const [showConnectForm, setShowConnectForm] = useState(false)
  const [url, setUrl] = useState('ws://127.0.0.1:4455')
  const [password, setPassword] = useState('')
  /** 저장된 접속 정보를 복원했는지 — 복원한 경우에만 자동 연결을 시도한다. */
  const [restored, setRestored] = useState(false)

  const [showStartForm, setShowStartForm] = useState(false)
  const [title, setTitle] = useState('')
  const [calendarEventId, setCalendarEventId] = useState<number | null>(null)
  const [prefilled, setPrefilled] = useState(false)

  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const connected = obs.status === 'CONNECTED'
  const live = sessionId !== null
  const obsStatus = obs.status
  const obsConnect = obs.connect

  /** 지난번 접속 정보를 복원해 연결 폼을 미리 채운다. */
  useEffect(() => {
    let active = true
    void window.api.obs.getCredentials().then((saved) => {
      if (!active || !saved) return
      setUrl(saved.url)
      setPassword(saved.password)
      setRestored(true)
    })
    return () => {
      active = false
    }
  }, [])

  /**
   * 복원한 정보로 자동 연결한다. 방송 시작에 OBS 연결이 필요해진 뒤로는 매 방송마다
   * 같은 값을 다시 넣는 일이 생기기 때문이다.
   * OBS가 꺼져 있는 건 정상이므로 실패해도 알리지 않는다 — 사용자가 직접 연결하면 된다.
   */
  useEffect(() => {
    if (!restored || autoConnectTried || obsStatus !== 'DISCONNECTED') return
    autoConnectTried = true
    void obsConnect(url, password || undefined).catch(() => undefined)
  }, [restored, obsStatus, url, password, obsConnect])

  /**
   * 시작 폼을 열 때 예정 방송으로 제목을 한 번 채운다.
   * 예정 방송이 없어도 제목만 직접 넣으면 시작할 수 있다(calendarEventId 없는 즉흥 방송).
   * TODO: 지금은 대시보드 전체 페이로드를 받아 쓴다. 경량 엔드포인트가 생기면 교체한다.
   */
  useEffect(() => {
    if (!showStartForm || prefilled) return
    let active = true
    void getDashboard()
      .then((data) => {
        if (!active) return
        if (data.upcomingBroadcast) {
          setTitle(data.upcomingBroadcast.title)
          setCalendarEventId(data.upcomingBroadcast.calendarEventId)
        }
      })
      // 예정 방송 조회 실패는 시작을 막을 이유가 아니다 — 제목을 직접 넣으면 된다.
      .catch(() => undefined)
      .finally(() => {
        if (active) setPrefilled(true)
      })
    return () => {
      active = false
    }
  }, [showStartForm, prefilled])

  const handleConnect = async (): Promise<void> => {
    setError(null)
    setBusy(true)
    try {
      await obs.connect(url, password || undefined)
      setShowConnectForm(false)
    } catch (e) {
      setError(e instanceof Error ? e.message : '연결에 실패했어요')
    } finally {
      setBusy(false)
    }
  }

  const handleStart = async (): Promise<void> => {
    if (!platformAccount) return
    const trimmed = title.trim()
    if (!trimmed) {
      setError('방송 제목을 입력해 주세요.')
      return
    }

    setBusy(true)
    setError(null)
    try {
      const result = await startSession({
        platformAccountId: platformAccount.platformAccountId,
        calendarEventId,
        title: trimmed,
        category: null,
        language: DEFAULT_LANGUAGE
      })
      // 세션이 먼저다. OBS·녹화 보고가 실패해도 세션은 살려 둔다 — 사용자가 OBS만
      // 다시 손보면 되지, 방송 자체를 다시 시작할 필요는 없어야 한다.
      try {
        await obs.startStream()
        await obs.startRecord()
        // baseWallClock·baseTimecodeMs는 같은 순간에 읽어야 한다 — GetRecordStatus 응답
        // 직후를 "같은 순간"으로 본다.
        const recordStatus = await obs.getRecordStatus()
        const now = new Date().toISOString()
        await startRecording(result.sessionId, {
          startedAt: now,
          baseWallClock: now,
          baseTimecodeMs: recordStatus.outputDuration
        })
      } catch (e) {
        setError(e instanceof Error ? e.message : 'OBS 송출·녹화를 시작하지 못했어요.')
      }
      setShowStartForm(false)
      onStarted(result.sessionId)
    } catch (e) {
      if (e instanceof ApiError) {
        if (e.code === 'SESSION_ALREADY_LIVE') {
          // 이미 진행 중인 세션이 있다 — 새로 만들지 말고 그쪽으로 붙는다.
          const current = await fetchCurrentSession()
          if (current) {
            setShowStartForm(false)
            onStarted(current.sessionId)
            return
          }
          setError('이미 진행 중인 방송이 있어요.')
        } else if (
          e.code === 'PLATFORM_REAUTH_REQUIRED' ||
          e.code === 'PLATFORM_ACCOUNT_NOT_FOUND'
        ) {
          navigate(PATHS.platformConnect)
          return
        } else if (e.code === 'CALENDAR_EVENT_STATE_CONFLICT') {
          setError('이미 시작된 일정이에요. 새로고침해 주세요.')
        } else {
          setError(e.message)
        }
      } else {
        setError('방송을 시작하지 못했어요.')
      }
    } finally {
      setBusy(false)
    }
  }

  const handleEnd = async (): Promise<void> => {
    if (sessionId === null) return
    setBusy(true)
    setError(null)

    // OBS·녹화 정리는 최선 노력이다. 실패해도 아래 세션 종료는 반드시 진행한다 —
    // OBS 사고 때문에 분석 세션이 LIVE로 남아버리면 사용자가 손쓸 방법이 없다.
    if (obs.streaming) {
      await obs.stopStream().catch(() => undefined)
    }
    if (obs.recording) {
      try {
        const { outputPath } = await obs.stopRecord()
        await finishRecording(sessionId, {
          endedAt: new Date().toISOString(),
          outputPath,
          status: 'COMPLETED'
        })
      } catch {
        // 녹화가 돌고 있었는데 결과 파일 경로를 못 얻었다 — COMPLETED로 잘못 보고하면
        // 서버가 존재하지 않(거나 잘린)는 파일로 클립 작업을 만든다. FAILED로 보고해
        // 그 세션의 클립 작업 자체가 생기지 않게 한다.
        await finishRecording(sessionId, {
          endedAt: new Date().toISOString(),
          outputPath: NO_OUTPUT_PATH,
          status: 'FAILED'
        }).catch(() => undefined)
      }
    }
    onStop()

    try {
      await endSession(sessionId)
      navigate(PATHS.sessionReportDetail.replace(':sessionId', String(sessionId)))
    } catch (e) {
      if (e instanceof ApiError && e.code === 'SESSION_ALREADY_ENDED') {
        // 이미 끝난 세션 — 실패로 취급하지 않고 리포트로 보낸다 (멱등 취급).
        navigate(PATHS.sessionReportDetail.replace(':sessionId', String(sessionId)))
        return
      }
      if (e instanceof ApiError && e.code === 'SESSION_NOT_FOUND') {
        navigate(PATHS.dashboard)
        return
      }
      setError(e instanceof ApiError ? e.message : '방송 종료에 실패했어요.')
    } finally {
      setBusy(false)
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

      {live ? (
        <AlertDialog>
          <AlertDialogTrigger asChild>
            <Button size="sm" variant="destructive" disabled={busy}>
              {busy ? '종료하는 중…' : '방송 종료'}
            </Button>
          </AlertDialogTrigger>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>방송을 종료할까요?</AlertDialogTitle>
              <AlertDialogDescription>
                OBS 송출과 음성 분석을 멈추고 방송을 끝냅니다. 종료 후 리포트 생성이 시작되며, 이
                작업은 되돌릴 수 없어요.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>취소</AlertDialogCancel>
              <AlertDialogAction onClick={() => void handleEnd()}>종료</AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      ) : connected ? (
        <>
          <Button size="sm" onClick={() => setShowStartForm((v) => !v)}>
            방송 시작
          </Button>
          <Button size="sm" variant="ghost" onClick={() => obs.disconnect()}>
            연결 해제
          </Button>
        </>
      ) : (
        <Button size="sm" variant="outline" onClick={() => setShowConnectForm((v) => !v)}>
          OBS 연결
        </Button>
      )}

      {/* OBS 연결 폼 */}
      {showConnectForm && !connected && (
        <div className="absolute top-full right-0 z-50 mt-2 flex w-72 flex-col gap-2 rounded-lg border bg-card p-3 shadow-md">
          <label className="text-xs font-medium text-muted-foreground">WebSocket 주소</label>
          <Input
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder="ws://127.0.0.1:4455"
          />
          <label className="text-xs font-medium text-muted-foreground">비밀번호</label>
          <Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
          {error && <p className="text-xs text-destructive">{error}</p>}
          <Button size="sm" onClick={() => void handleConnect()} disabled={busy}>
            {busy ? '연결 중…' : '연결'}
          </Button>
          <p className="text-micro leading-relaxed text-muted-foreground">
            OBS · 도구 → WebSocket 서버 설정에서 서버를 활성화해야 해요. 방송은 OBS가 연결된 뒤에
            시작할 수 있어요. 연결에 성공하면 접속 정보를 기억해 다음부터 자동으로 연결해요.
          </p>
          {/* 하이라이트 클립을 정확히 잘라내려면 OBS 녹화 설정이 이 두 가지를 지켜야 한다. */}
          <p className="border-t pt-2 text-micro leading-relaxed text-muted-foreground">
            녹화 설정 확인: <strong className="font-medium">스트림 인코더 재사용</strong>을 켜야
            게임 프레임이 안 떨어지고, <strong className="font-medium">자동 분할</strong>은 꺼야
            해요(켜져 있으면 파일이 쪼개지면서 타임코드가 리셋돼 엉뚱한 구간이 잘려요).
          </p>
        </div>
      )}

      {/* 방송 시작 폼 */}
      {showStartForm && connected && !live && (
        <div className="absolute top-full right-0 z-50 mt-2 flex w-72 flex-col gap-2 rounded-lg border bg-card p-3 shadow-md">
          {platformAccountLoading ? (
            <p className="text-caption text-muted-foreground">연동 계정을 확인하는 중…</p>
          ) : platformAccount ? (
            <>
              <label className="text-xs font-medium text-muted-foreground">방송 제목</label>
              <Input
                value={title}
                maxLength={TITLE_MAX}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="오늘 방송 제목"
              />
              <p className="text-micro text-muted-foreground">
                {calendarEventId === null
                  ? '예정된 일정 없이 시작해요.'
                  : '예정된 일정과 연결해 시작해요.'}
              </p>
              {error && <p className="text-xs text-destructive">{error}</p>}
              <Button size="sm" onClick={() => void handleStart()} disabled={busy}>
                {busy ? '시작하는 중…' : '방송 시작'}
              </Button>
            </>
          ) : (
            <>
              <p className="text-caption text-muted-foreground">
                연동된 방송 플랫폼이 없어 시작할 수 없어요.
              </p>
              <Button asChild size="sm" variant="outline">
                <Link to={PATHS.platformConnect}>플랫폼 연동하러 가기</Link>
              </Button>
            </>
          )}
        </div>
      )}

      {/* 폼 밖에서 난 오류 (종료 실패 등) */}
      {error && !showConnectForm && !showStartForm && (
        <span className="max-w-48 truncate text-xs text-destructive" title={error}>
          {error}
        </span>
      )}
    </div>
  )
}
