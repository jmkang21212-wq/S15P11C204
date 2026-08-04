import { ApiError, apiFetch } from './client'
import type {
  Portfolio,
  PortfolioCreate,
  PortfolioUpdate,
  PublicPortfolio
} from '@renderer/types/portfolio'

/**
 * SC-10 포트폴리오 데이터 소스.
 *
 * - `GET   /portfolio/me`            내 포트폴리오 (API 명세서 §12.1)
 * - `POST  /portfolio/me`            생성 (§12 신설)
 * - `PATCH /portfolio/me`            부분 수정 (§12.2)
 * - `GET   /portfolios/{publicSlug}` 공개 뷰, 인증 불필요 (§12.4)
 *
 * 응답 DTO가 `types/portfolio.ts`와 필드까지 같아서 변환 계층을 두지 않는다.
 */

/**
 * 공개 뷰가 볼 수 없는 포트폴리오인지. 비공개와 미존재를 구분하지 않는다 —
 * BE가 의도적으로 둘을 같은 404(`PORTFOLIO_NOT_FOUND`)로 합쳐, 비공개 포트폴리오의
 * 존재 자체를 노출하지 않는다.
 */
export function isPortfolioNotPublic(error: unknown): boolean {
  return error instanceof ApiError && error.code === 'PORTFOLIO_NOT_FOUND'
}

/**
 * 내 포트폴리오가 없는 이유. BE가 404를 두 코드로 나눠 보내므로 편집 화면이
 * "만들 수 있음"과 "아직 자격 없음"을 다르게 안내할 수 있다.
 * 그 밖의 오류(네트워크·500 등)는 null이다 — 진짜 실패로 다뤄야 한다.
 */
export type PortfolioAbsence = 'NOT_CREATED' | 'NOT_ELIGIBLE'

export function portfolioAbsence(error: unknown): PortfolioAbsence | null {
  if (!(error instanceof ApiError)) return null
  if (error.code === 'PORTFOLIO_NOT_CREATED') return 'NOT_CREATED'
  if (error.code === 'PORTFOLIO_NOT_ELIGIBLE') return 'NOT_ELIGIBLE'
  return null
}

export async function fetchMyPortfolio(): Promise<Portfolio> {
  return apiFetch<Portfolio>('/portfolio/me')
}

/**
 * 포트폴리오를 만든다. 종료된 방송이 1건 이상 있어야 하고(`PORTFOLIO_NOT_ELIGIBLE`),
 * 스트리머당 하나만 가질 수 있다(`PORTFOLIO_ALREADY_EXISTS`).
 *
 * 서버는 생성과 동시에 태그를 `streamer_tags`에 반영하고 실적을 집계해서, 응답에 이미
 * 실제 시청자·방송 수가 채워져 있다.
 */
export async function createMyPortfolio(input: PortfolioCreate): Promise<Portfolio> {
  return apiFetch<Portfolio>('/portfolio/me', {
    method: 'POST',
    body: JSON.stringify(input)
  })
}

/**
 * 전달한 필드만 수정한다. BE는 `PatchValue`로 "미전송"과 "명시적 null"을 구분하므로,
 * 값을 비우려면 `null`을 실어야 하고 건드리지 않을 필드는 키를 빼야 한다 —
 * `JSON.stringify`가 `undefined` 키를 지우므로 `PortfolioUpdate`를 그대로 보내면 된다.
 */
export async function updateMyPortfolio(patch: PortfolioUpdate): Promise<Portfolio> {
  return apiFetch<Portfolio>('/portfolio/me', {
    method: 'PATCH',
    body: JSON.stringify(patch)
  })
}

/** 공개 포트폴리오 조회. 인증이 필요 없고, 비공개·미존재는 같은 404다 (§12.4). */
export async function fetchPublicPortfolio(publicSlug: string): Promise<PublicPortfolio> {
  return apiFetch<PublicPortfolio>(`/portfolios/${encodeURIComponent(publicSlug)}`)
}
