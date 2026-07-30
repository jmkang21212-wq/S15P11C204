import { AI_WS_URL } from '@renderer/api/config'
import type {
  AlertEvent,
  AudioStreamEvent,
  AudioStreamStatus,
  PromiseEvent
} from '@renderer/types/audioStream'

/**
 * 마이크 → PCM16 → AI 서버 WebSocket 스트리밍 (AI/FRONTEND_API.md).
 *
 * React와 무관한 클래스로 두어 콘솔·미니 창 등 어디서든 재사용할 수 있게 한다.
 * 무음을 걸러 보내지 않는다 — 서버 VAD가 발화 구간을 골라내므로 그냥 계속 흘려보낸다.
 */

/** 서버 VAD가 16kHz 고정이다. 다른 값을 보내면 에러 없이 아무것도 감지되지 않는다. */
const TARGET_SAMPLE_RATE = 16000
const WORKLET_NAME = 'pcm-worklet'
const RECONNECT_DELAY_MS = 2000
const MAX_RECONNECT_ATTEMPTS = 5

interface AudioStreamHandlers {
  onStatus: (status: AudioStreamStatus) => void
  onAlert: (event: AlertEvent) => void
  onPromise: (event: PromiseEvent) => void
  onError: (message: string) => void
}

/**
 * AudioWorklet 모듈 주소.
 * public/에 원본이 그대로 서빙되므로 페이지 기준 상대 경로로 해석한다
 * (dev의 http, 패키징된 앱의 file:// 양쪽에서 동작).
 */
function workletUrl(): string {
  return new URL('pcm-worklet.js', window.location.href).href
}

export class AudioStreamClient {
  private readonly handlers: AudioStreamHandlers
  /** 스트리밍을 유지해야 하는 상태인지 — 의도치 않은 끊김과 사용자 중지를 구분한다. */
  private desired = false
  private baseSessionId = ''
  private ws: WebSocket | null = null
  private ctx: AudioContext | null = null
  private stream: MediaStream | null = null
  private node: AudioWorkletNode | null = null
  private attempts = 0
  private reconnectTimer: number | null = null

  constructor(handlers: AudioStreamHandlers) {
    this.handlers = handlers
  }

  /** 방송 시작 시 호출. baseSessionId에 접속마다 고유 접미사를 붙여 세션을 만든다. */
  async start(baseSessionId: string): Promise<void> {
    if (this.desired) return
    this.desired = true
    this.baseSessionId = baseSessionId
    this.attempts = 0
    await this.connect()
  }

  /** 방송 종료 시 호출. 마이크 트랙까지 정지한다. */
  stop(): void {
    this.desired = false
    this.clearReconnectTimer()
    this.teardown()
    this.handlers.onStatus('IDLE')
  }

  private async connect(): Promise<void> {
    this.handlers.onStatus('CONNECTING')
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({
        audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true }
      })
      if (!this.desired) return this.teardown()

      const ctx = new AudioContext({ sampleRate: TARGET_SAMPLE_RATE })
      this.ctx = ctx
      if (ctx.sampleRate !== TARGET_SAMPLE_RATE) {
        throw new Error(`마이크 샘플레이트가 ${ctx.sampleRate}Hz예요 (16000Hz 필요)`)
      }
      await ctx.audioWorklet.addModule(workletUrl())
      if (!this.desired) return this.teardown()

      // 같은 session_id로 두 번 접속하면 앞 연결이 조용히 죽는다. 접속마다 새로 만든다.
      const sessionId = `${this.baseSessionId}-${crypto.randomUUID().slice(0, 8)}`
      const ws = new WebSocket(AI_WS_URL)
      ws.binaryType = 'arraybuffer'
      this.ws = ws

      ws.onopen = () => {
        // 첫 메시지는 반드시 핸드셰이크 JSON이어야 한다 (10초 제한).
        // TODO: token은 아직 검증되지 않는다. Spring JWT로 교체될 자리.
        ws.send(JSON.stringify({ session_id: sessionId, token: 'dev' }))
      }
      ws.onmessage = (event) => this.handleMessage(event.data)
      ws.onerror = () => this.handlers.onError('AI 서버에 연결할 수 없어요')
      ws.onclose = () => this.handleUnexpectedClose()
    } catch (e) {
      this.teardown()
      this.handlers.onStatus('ERROR')
      this.handlers.onError(
        e instanceof Error ? e.message : '음성 스트리밍을 시작할 수 없어요'
      )
    }
  }

  private handleMessage(raw: unknown): void {
    if (typeof raw !== 'string') return
    let event: AudioStreamEvent
    try {
      event = JSON.parse(raw)
    } catch {
      return
    }

    switch (event.type) {
      case 'ready':
        // ready를 받은 뒤에야 오디오를 보내기 시작한다.
        this.beginSending()
        this.attempts = 0
        this.handlers.onStatus('STREAMING')
        break
      case 'alert':
        this.handlers.onAlert(event)
        break
      case 'promise':
        this.handlers.onPromise(event)
        break
      default:
        // transcript는 개발용이라 구독하지 않는다.
        break
    }
  }

  private beginSending(): void {
    if (!this.ctx || !this.stream || this.node) return
    const node = new AudioWorkletNode(this.ctx, WORKLET_NAME)
    this.node = node
    node.port.onmessage = (event) => {
      if (this.ws?.readyState === WebSocket.OPEN) this.ws.send(event.data)
    }
    this.ctx.createMediaStreamSource(this.stream).connect(node)
    // Chromium에서 워클릿이 멈추지 않도록 연결만 해둔다 (출력이 비어 소리는 나지 않는다).
    node.connect(this.ctx.destination)
  }

  /** 방송 중 끊김은 자동 복구한다. 재접속 시 session_id는 새로 만들어야 한다. */
  private handleUnexpectedClose(): void {
    if (!this.desired) return
    this.teardown()

    if (this.attempts >= MAX_RECONNECT_ATTEMPTS) {
      this.desired = false
      this.handlers.onStatus('ERROR')
      this.handlers.onError('AI 서버 연결이 끊겼어요. 다시 시도해 주세요.')
      return
    }

    this.attempts += 1
    this.handlers.onStatus('CONNECTING')
    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null
      void this.connect()
    }, RECONNECT_DELAY_MS)
  }

  private clearReconnectTimer(): void {
    if (this.reconnectTimer !== null) {
      window.clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
  }

  private teardown(): void {
    if (this.ws) {
      // 핸들러를 먼저 떼야 정리 과정에서 재연결이 다시 걸리지 않는다.
      this.ws.onclose = null
      this.ws.onerror = null
      this.ws.onmessage = null
      if (this.ws.readyState <= WebSocket.OPEN) this.ws.close()
      this.ws = null
    }
    if (this.node) {
      this.node.port.onmessage = null
      this.node.disconnect()
      this.node = null
    }
    if (this.ctx) {
      void this.ctx.close().catch(() => undefined)
      this.ctx = null
    }
    if (this.stream) {
      this.stream.getTracks().forEach((track) => track.stop())
      this.stream = null
    }
  }
}
