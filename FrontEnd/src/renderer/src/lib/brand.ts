/**
 * 외부 브랜드 규정 색 — 디자인 시스템 팔레트(assets/theme/*)에 넣지 않는다.
 *
 * 네이버·치지직·유튜브 색은 각 서비스의 브랜드 가이드라인이 정한 값이라
 * 우리 디자인 시스템을 교체해도 바뀌면 안 된다. 그래서 토큰 체계 밖에
 * 이 파일 하나로 모아 "의도적으로 토큰이 아님"을 코드로 드러낸다.
 */

export const NAVER_GREEN = '#03c75a'

export const PLATFORM_BRAND_COLOR: Record<'CHZZK' | 'YOUTUBE', string> = {
  CHZZK: '#00ffa3',
  YOUTUBE: '#ff0000'
}
