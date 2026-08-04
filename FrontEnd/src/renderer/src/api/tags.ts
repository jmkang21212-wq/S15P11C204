import { apiFetch } from './client'
import type { PortfolioTag } from '@renderer/types/portfolio'

/**
 * 태그 카탈로그 (`GET /tags`).
 *
 * 명세 원안에 조회 경로가 없어 BE가 신설한 엔드포인트다(`TagCatalogController`) —
 * 이게 없으면 클라이언트가 `tagIds`를 알 방법이 없어 포트폴리오를 만들 수 없다.
 *
 * 응답이 `PortfolioTag`(tagId·name·tagType)와 같은 형태라 타입을 새로 만들지 않고
 * 재사용한다. 태그는 포트폴리오 전용이 아니라 `streamer_tags` 한 곳에 저장되는
 * 프로필 태그이기도 하다.
 */
export async function fetchTagCatalog(): Promise<PortfolioTag[]> {
  return apiFetch<PortfolioTag[]>('/tags')
}
