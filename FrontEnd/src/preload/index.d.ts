import { ElectronAPI } from '@electron-toolkit/preload'
import type { BroadcastSessionDetail } from '@renderer/types/broadcastSession'
import type { LiveSnapshot } from '@renderer/types/console'

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

/** 지난번 연결에 성공한 OBS 접속 정보. 비밀번호 보관은 main(safeStorage)이 맡는다. */
export interface ObsCredentials {
  url: string
  /** 저장된 비밀번호. 없거나 OS 암호화를 쓸 수 없는 환경이면 빈 문자열. */
  password: string
}

export interface ObsBridge {
  connect: (url: string, password?: string) => Promise<ObsState>
  disconnect: () => Promise<void>
  getState: () => Promise<ObsState>
  /** 저장된 접속 정보. 없으면 null. 연결 성공 시 main이 자동으로 갱신한다. */
  getCredentials: () => Promise<ObsCredentials | null>
  startRecord: () => Promise<void>
  /** 녹화를 멈추고 결과 파일 경로를 반환한다. */
  stopRecord: () => Promise<{ outputPath: string }>
  getRecordStatus: () => Promise<ObsRecordStatus>
  startStream: () => Promise<void>
  stopStream: () => Promise<void>
  /** 상태 변경 구독. 반환 함수 호출로 구독 해제. */
  onState: (callback: (state: ObsState) => void) => () => void
}

/** clip:cut 요청 — ClipJob(api/clips.ts)에서 ffmpeg에 필요한 필드만 추린 것. */
export interface ClipCutRequest {
  recordingPath: string
  clipStartSec: number
  clipDurationSec: number
}

export type ClipCutResult =
  | { ok: true; clip: Uint8Array; thumbnail: Uint8Array }
  | {
      ok: false
      /** true면 재시도해도 같은 실패(녹화본 소실 등) — 즉시 포기해야 한다. */
      permanent: boolean
      reason: string
    }

export interface ClipBridge {
  /** ffmpeg로 컷+썸네일을 만든다. 업로드는 렌더러가 반환된 바이트로 직접 한다. */
  cut: (request: ClipCutRequest) => Promise<ClipCutResult>
}

/**
 * 렌더러에 노출하는 앱 전용 브릿지.
 * 화면별로 늘리지 말고 공통 동작만 여기에 둔다.
 */
export interface LaiveApi {
  /** 기본 브라우저로 URL을 연다 (http/https만 허용). */
  openExternal: (url: string) => Promise<void>
  /** 파일을 내려받는다. 저장 위치는 Electron 기본 다이얼로그로 고른다. */
  downloadFile: (url: string) => Promise<void>
  /** 미니 모드 창을 연다 (이미 있으면 앞으로 가져온다). */
  openMiniWindow: () => void
  /** 미니 모드 창을 닫는다. */
  closeMiniWindow: () => void
  /** 메인 창을 앞으로 가져온다. */
  focusMainWindow: () => void
  /** OBS(obs-websocket) 연결·제어. */
  obs: ObsBridge
  /** OAuth 인앱 창 (네이버 로그인·플랫폼 연동 공용). */
  oauth: OAuthBridge
  /** 실시간 브리핑 중계 (콘솔 → 미니 창). */
  briefing: BriefingBridge
  /** 진행 중 방송 세션 상태 릴레이 (메인 창 → 미니·오버레이 창). */
  session: SessionBridge
  /** 실시간 콘솔 스냅샷 릴레이 (콘솔 창 → 미니·오버레이 창). */
  snapshot: SnapshotBridge
  /** 반복 질문 답변 완료 요청 릴레이 (미니 창 → 콘솔 창). */
  question: QuestionBridge
  /** 하이라이트 클립 컷 (ffmpeg). */
  clip: ClipBridge
}

/** 반복 질문 dismiss 요청 payload. */
export interface DismissQuestionRequest {
  sessionId: number
  questionId: number
}

/**
 * 반복 질문 답변 완료 요청 릴레이.
 * 미니 창은 인증 토큰이 없어 dismiss API를 직접 부를 수 없다 — 콘솔 창(owner)이
 * 요청을 받아 대신 호출한다. 다른 릴레이와 방향이 반대다.
 */
export interface QuestionBridge {
  /** 미니 창에서 호출 — 콘솔 창에 처리를 요청한다. */
  requestDismiss: (sessionId: number, questionId: number) => void
  /** 콘솔 창에서 구독 — 반환 함수 호출로 구독 해제. */
  onDismissRequest: (callback: (payload: DismissQuestionRequest) => void) => () => void
}

export interface OAuthBridge {
  /** OAuth 인앱 창. returnUri 복귀 시 그 URL, 취소 시 null. */
  open: (startUrl: string, returnUriPrefix: string) => Promise<string | null>
}

/**
 * 창 간 실시간 브리핑 중계.
 * main은 페이로드를 해석하지 않으므로 여기서는 unknown으로 두고,
 * 타입 확정은 렌더러(useLiveBriefings)에서 한 번만 한다.
 */
export interface BriefingBridge {
  /** 다른 창(미니 모드)에 브리핑을 중계한다. */
  publish: (briefing: unknown) => void
  /** main이 보관한 최근 브리핑 (최신순). */
  recent: () => Promise<unknown[]>
  /** 중계된 브리핑 구독. 반환 함수 호출로 구독 해제. */
  onNew: (callback: (briefing: unknown) => void) => () => void
}

export interface SessionBridge {
  /** owner(메인) 창이 새로 조회한 세션 상태를 다른 창에 전파하도록 main에 넘긴다. */
  publish: (session: BroadcastSessionDetail | null) => void
  /** follower(미니·오버레이) 창이 열릴 때 초기값을 받는다. */
  getState: () => Promise<BroadcastSessionDetail | null>
  /** 상태 변경 구독. 반환 함수 호출로 구독 해제. */
  onState: (callback: (session: BroadcastSessionDetail | null) => void) => () => void
}

export interface SnapshotBridge {
  /** owner(콘솔) 창이 새로 조회한 스냅샷을 다른 창에 전파하도록 main에 넘긴다. */
  publish: (snapshot: LiveSnapshot | null) => void
  /** follower(미니·오버레이) 창이 열릴 때 초기값을 받는다. */
  getState: () => Promise<LiveSnapshot | null>
  /** 상태 변경 구독. 반환 함수 호출로 구독 해제. */
  onState: (callback: (snapshot: LiveSnapshot | null) => void) => () => void
}

declare global {
  interface Window {
    electron: ElectronAPI
    api: LaiveApi
  }
}
