import { useCallback, useSyncExternalStore } from 'react'
import { AudioStreamClient } from '@renderer/lib/AudioStreamClient'
import type {
  AlertCategory,
  AlertEvent,
  AudioStreamStatus,
  PromiseEvent
} from '@renderer/types/audioStream'
import type { Briefing, RiskLevel } from '@renderer/types/console'

const CATEGORY_LABEL: Record<AlertCategory, string> = {
  profanity: '비속어',
  insult: '모욕',
  threat: '위협',
  harassment: '괴롭힘',
  discrimination: '차별'
}

/** AI 서버의 위험 발언 경고를 콘솔 브리핑 카드로 변환한다. */
function alertToBriefing(event: AlertEvent, briefingId: number): Briefing {
  const category = CATEGORY_LABEL[event.category] ?? event.category
  const advice =
    event.source === 'llm' ? event.reason : event.hits?.map((hit) => `"${hit.matched}"`).join(', ')

  return {
    briefingId,
    briefingType: 'RISK',
    level: event.severity.toUpperCase() as RiskLevel,
    message:
      event.source === 'dictionary'
        ? `${category} 표현이 감지됐어요.`
        : `문맥상 ${category}으로 판단되는 발언이 감지됐어요.`,
    quote: event.text,
    advice,
    // 발화가 끝난 시점을 브리핑 시각으로 쓴다.
    createdAt: new Date(event.end_ts * 1000).toISOString()
  }
}

/** AI 서버의 약속 감지를 콘솔 브리핑 카드로 변환한다. */
function promiseToBriefing(event: PromiseEvent, briefingId: number): Briefing {
  // 기한은 서버가 절대 시각까지 계산해서 보낸다. 프론트가 파싱하지 않는다.
  const deadline = event.deadline_at
    ? `기한 ${new Date(event.deadline_at * 1000).toLocaleDateString('ko-KR')}`
    : event.condition
      ? `조건 ${event.condition}`
      : '기한 없음'

  return {
    briefingId,
    briefingType: 'PROMISE',
    message: event.content,
    // Spring 저장 실패 시 null — 그때는 확정/무시 버튼을 못 붙이므로 undefined로 통일한다.
    promiseId: event.promise_id ?? undefined,
    quote: event.text,
    advice: `${deadline} · 확신 ${Math.round(event.confidence * 100)}%`,
    createdAt: new Date(event.spoken_at * 1000).toISOString()
  }
}

export interface UseAudioStreamResult {
  status: AudioStreamStatus
  error: string | null
  /** 실시간으로 도착한 브리핑 (최신순) */
  briefings: Briefing[]
  /** WS 수신 시작 시각(ISO). 브리핑 병합 컷오버로 쓴다. 시작 전이면 null. */
  startedAt: string | null
  start: () => void
  /** 마이크만 끈다. 브리핑 기록은 남는다. */
  stop: () => void
  /** 방송 종료 — 마이크를 놓고 이 방송의 브리핑·컷오버를 버린다. */
  end: () => void
}

/**
 * 오디오 스트림 저장소 — 모듈 레벨에 둔다.
 *
 * 전에는 `useAudioStream`이 클라이언트를 소유하고 언마운트에서 `stop()`을 불렀다. 그러면
 * 방송 중에 대시보드·일정 등으로 이동하는 순간 콘솔 페이지가 언마운트되어 마이크와 AI 서버
 * WebSocket이 끊긴다. 방송은 계속되는데 분석만 멈추는 것이라 사용자가 알아채기도 어렵다.
 *
 * 그래서 수명을 화면이 아니라 **방송**에 묶었다. 마이크는 방송 종료(`end`)와 로그아웃
 * (`reset`)에서만 놓는다. `authStore`와 같은 `useSyncExternalStore` 패턴이라 여러 화면이
 * 같은 상태를 구독할 수 있다.
 *
 * 브리핑을 여기 쌓아 둬도 유실되지 않는다 — 이미 main의 중계 버퍼(최근 20건)가 미니 창에
 * 같은 값을 뿌리고 있고, 콘솔이 다시 마운트되면 이 저장소 값이 그대로 남아 있다.
 */
interface AudioStreamState {
  status: AudioStreamStatus
  error: string | null
  briefings: Briefing[]
  /**
   * WS 수신을 시작한 시각(ISO). 브리핑 병합 컷오버로 쓴다 — 이 시각 이후의 RISK·PROMISE는
   * WS가 전담하고 스냅샷에서는 이전 것만 히스토리로 받는다(`lib/briefings.ts`).
   *
   * 콘솔 마운트 시각이 아니라 여기 두는 이유는 스트림이 화면보다 오래 살기 때문이다.
   * 마운트 시각을 쓰면 방송 중에 나갔다 돌아올 때 컷오버가 현재로 밀려, 그 사이 WS로 이미
   * 받은 브리핑이 스냅샷에서도 히스토리로 들어와 두 번 보인다.
   *
   * 같은 방송 안에서 마이크를 껐다 켜도 갱신하지 않는다 — 저장소에 첫 시작 이후의 실시간
   * 브리핑이 계속 쌓여 있으므로 컷오버도 첫 시작에 맞춰야 겹치지 않는다. 다른 방송으로
   * 넘어갈 때만 새로 잡는다(`start` 참고).
   */
  startedAt: string | null
}

let state: AudioStreamState = {
  status: 'IDLE',
  error: null,
  briefings: [],
  startedAt: null
}
const listeners = new Set<() => void>()
let client: AudioStreamClient | null = null
/** 실시간 이벤트에는 서버 briefingId가 없다. 스냅샷 id와 겹치지 않게 음수를 쓴다. */
let localId = 0
/** 지금 쌓고 있는 브리핑이 어느 방송의 것인지. 방송이 바뀌면 저장소를 비우는 기준. */
let startedSessionId: number | null = null

function setState(next: Partial<AudioStreamState>): void {
  state = { ...state, ...next }
  for (const listener of listeners) listener()
}

/** 브리핑을 저장소에 쌓고, 미니 창 등 다른 창에도 중계한다. */
function emit(briefing: Briefing): void {
  setState({ briefings: [briefing, ...state.briefings] })
  window.api.briefing.publish(briefing)
}

function ensureClient(): AudioStreamClient {
  if (client === null) {
    client = new AudioStreamClient({
      onStatus: (status) => setState({ status }),
      onError: (error) => setState({ error }),
      onAlert: (event) => emit(alertToBriefing(event, --localId)),
      onPromise: (event) => emit(promiseToBriefing(event, --localId))
    })
  }
  return client
}

export const audioStreamStore = {
  get: (): AudioStreamState => state,
  subscribe: (listener: () => void): (() => void) => {
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  },
  /**
   * 다른 방송으로 시작하면 브리핑과 컷오버를 새로 잡는다. 스트림이 화면보다 오래 살면서
   * 방송 종료 후에도 저장소가 남으므로, 이게 없으면 다음 방송 콘솔에 지난 방송의 브리핑이
   * 그대로 보이고 컷오버도 지난 방송 시각에 머물러 새 스냅샷 히스토리가 가려진다.
   *
   * @param baseSessionId AI 서버 세션 식별용 문자열. 접속마다 고유 접미사가 붙는다
   * @param broadcastSessionId broadcast_sessions.id. AI 서버가 이 값으로 전사·경고를
   *        Spring에 저장한다. null이면 실시간 표시는 되지만 DB 저장이 건너뛰어진다
   */
  start: (baseSessionId: string, broadcastSessionId: number | null): void => {
    const sameSession = broadcastSessionId === startedSessionId
    startedSessionId = broadcastSessionId
    if (sameSession) {
      setState({ error: null })
    } else {
      localId = 0
      setState({ error: null, briefings: [], startedAt: new Date().toISOString() })
    }
    void ensureClient().start(baseSessionId, broadcastSessionId)
  },
  /**
   * 마이크와 WebSocket만 놓는다. 방송 중 마이크를 잠시 끄는 용도라 브리핑 기록은 남긴다 —
   * 지금까지 무슨 일이 있었는지가 이 화면의 본론이므로 마이크를 껐다고 지우면 안 된다.
   */
  stop: (): void => {
    client?.stop()
  },
  /**
   * 방송을 끝낸다. 마이크를 놓고 이 방송의 브리핑·컷오버를 모두 버린다.
   *
   * `stop`과 나눠 둔 이유는 저장소가 화면보다 오래 살기 때문이다. 종료 후 콘솔을 다시 열면
   * 진행 중 세션이 없어 시청자·여론은 비지만, 브리핑은 이 저장소가 들고 있어 지난 방송
   * 카드가 그대로 남는다.
   *
   * main의 중계 버퍼도 같이 비운다 — 미니 창은 브리핑을 그 버퍼에서 받으므로 여기만
   * 비우면 미니 창에 지난 방송이 남는다.
   */
  end: (): void => {
    client?.stop()
    localId = 0
    startedSessionId = null
    setState({ status: 'IDLE', error: null, briefings: [], startedAt: null })
    window.api?.briefing?.clear()
  },
  /**
   * 로그아웃 정리 — 다음 사용자에게 이전 방송의 브리핑이 보이면 안 된다.
   * `end`와 달리 클라이언트 자체를 버린다.
   *
   * 부팅 직후(토큰 복원 전)에도 불리므로 IPC는 브릿지 유무를 확인해서 부른다 —
   * 브라우저로 직접 연 개발 환경에는 window.api가 없다(useCurrentSession과 같은 사정).
   */
  reset: (): void => {
    client?.stop()
    client = null
    localId = 0
    startedSessionId = null
    setState({ status: 'IDLE', error: null, briefings: [], startedAt: null })
    // reset은 부팅 직후(토큰 복원 전)에도 불리므로 브릿지 유무를 확인한다 —
    // 브라우저로 직접 연 개발 환경에는 window.api가 없다(useCurrentSession과 같은 사정).
    window.api?.briefing?.clear()
  }
}

/**
 * 방송 음성을 AI 서버로 스트리밍하고, 돌아오는 경고·약속을 브리핑으로 노출한다.
 * 방송 시작/종료에서 start·stop을 호출한다.
 *
 * 화면을 벗어나도 스트림은 유지된다 — 위 저장소 주석 참고.
 */
export function useAudioStream(
  baseSessionId: string,
  broadcastSessionId: number | null
): UseAudioStreamResult {
  const { status, error, briefings, startedAt } = useSyncExternalStore(
    audioStreamStore.subscribe,
    audioStreamStore.get
  )

  const start = useCallback(() => {
    audioStreamStore.start(baseSessionId, broadcastSessionId)
  }, [baseSessionId, broadcastSessionId])

  const stop = useCallback(() => {
    audioStreamStore.stop()
  }, [])

  const end = useCallback(() => {
    audioStreamStore.end()
  }, [])

  return { status, error, briefings, startedAt, start, stop, end }
}
