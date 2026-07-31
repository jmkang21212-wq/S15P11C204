import { useCallback, useEffect, useRef, useState } from 'react'
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
  start: () => void
  stop: () => void
}

/**
 * 방송 음성을 AI 서버로 스트리밍하고, 돌아오는 경고·약속을 브리핑으로 노출한다.
 * 방송 시작/종료에서 start·stop을 호출한다.
 *
 * @param baseSessionId AI 서버 세션 식별용 문자열. 접속마다 고유 접미사가 붙는다
 * @param broadcastSessionId broadcast_sessions.id. AI 서버가 이 값으로 전사·경고를
 *        Spring에 저장한다. null이면 실시간 표시는 되지만 DB 저장이 건너뛰어진다
 */
export function useAudioStream(
  baseSessionId: string,
  broadcastSessionId: number | null
): UseAudioStreamResult {
  const [status, setStatus] = useState<AudioStreamStatus>('IDLE')
  const [error, setError] = useState<string | null>(null)
  const [briefings, setBriefings] = useState<Briefing[]>([])
  const clientRef = useRef<AudioStreamClient | null>(null)
  // 실시간 이벤트에는 서버 briefingId가 없다. 스냅샷 id와 겹치지 않게 음수를 쓴다.
  const localIdRef = useRef(0)

  useEffect(() => {
    const nextId = (): number => {
      localIdRef.current -= 1
      return localIdRef.current
    }
    /**
     * 화면에 반영하고, 미니 창 등 다른 창에도 같은 브리핑을 중계한다.
     * 오디오 스트림은 콘솔 창만 소유하므로 발행자도 이 훅 하나뿐이다.
     */
    const emit = (briefing: Briefing): void => {
      setBriefings((prev) => [briefing, ...prev])
      window.api.briefing.publish(briefing)
    }

    const client = new AudioStreamClient({
      onStatus: setStatus,
      onError: setError,
      onAlert: (event) => emit(alertToBriefing(event, nextId())),
      onPromise: (event) => emit(promiseToBriefing(event, nextId()))
    })
    clientRef.current = client
    // 화면을 벗어나면 마이크를 반드시 놓아준다.
    return () => {
      client.stop()
      clientRef.current = null
    }
  }, [])

  const start = useCallback(() => {
    setError(null)
    void clientRef.current?.start(baseSessionId, broadcastSessionId)
  }, [baseSessionId, broadcastSessionId])

  const stop = useCallback(() => {
    clientRef.current?.stop()
  }, [])

  return { status, error, briefings, start, stop }
}
