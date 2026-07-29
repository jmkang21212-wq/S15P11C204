/** 인증 응답 타입 — 백엔드 AuthTokenResponse / Streamer와 일치. */

export interface Streamer {
  streamerId: number
  email: string | null
  nickname: string
  profileImageUrl: string | null
}

export interface AuthTokenResponse {
  accessToken: string
  expiresIn: number
  streamer: Streamer
}
