/**
 * 마이크 Float32 샘플을 AI 서버가 요구하는 PCM16(리틀엔디안)으로 바꿔 메인 스레드로 넘긴다.
 *
 * public/에 두는 이유: AudioWorklet 모듈은 번들러 변환을 거치면 안 되고,
 * 페이지 기준 상대 경로로 로드해야 dev(http)와 패키징(file://) 양쪽에서 동작한다.
 */
class PcmWorklet extends AudioWorkletProcessor {
  process(inputs) {
    const channel = inputs[0] && inputs[0][0]
    if (channel) {
      const out = new Int16Array(channel.length)
      for (let i = 0; i < channel.length; i++) {
        const s = Math.max(-1, Math.min(1, channel[i]))
        out[i] = s < 0 ? s * 0x8000 : s * 0x7fff
      }
      // 버퍼 소유권을 넘겨 복사를 피한다.
      this.port.postMessage(out.buffer, [out.buffer])
    }
    // 출력에 아무것도 쓰지 않으므로 destination에 연결해도 소리는 나지 않는다.
    return true
  }
}

registerProcessor('pcm-worklet', PcmWorklet)
