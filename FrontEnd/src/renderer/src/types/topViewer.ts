/**
 * 열성 시청자 (FR-F-03).
 *
 * 출처: API 명세서 §10.6 `live-snapshot.topViewers` (방송 중) 와
 *       §11.1 `report.topViewers` (방송 후). **두 응답의 필드가 같아서** 타입도
 *       컴포넌트도 하나로 쓴다.
 *
 * 데이터 출처는 다르다 — 콘솔은 조회 시점의 채팅 집계라 3초마다 순위가 바뀌고,
 * 리포트는 방송 종료 시 확정 저장된 `viewer_scores`라 값이 고정이다. 화면에서는
 * 구별할 필요가 없지만, "리포트 숫자가 방송 중과 미세하게 다르다"는 문의가 오면
 * 이 차이 때문이다(마지막 몇 초의 채팅이 확정 집계에 더 들어간다).
 */
export interface TopViewer {
  viewerId: number
  /** 수집기가 관측한 표시용 닉네임. 아직 관측 전이면 null — `viewerAlias`로 폴백한다 */
  nickname: string | null
  /** 해시 파생 익명 별칭(viewer-a91c). 항상 채워진다 */
  viewerAlias: string
  /**
   * 분(minute)당 3개 캡을 적용한 채팅 점수.
   *
   * ⚠️ **실제 채팅 건수가 아니다.** 1분에 100개를 쳐도 3점이다(도배 방지).
   * "채팅 N건"으로 표기하면 거짓이 되므로 화면에 그대로 쓰지 않는다.
   */
  chatScore: number
  /** 채팅을 친 분(minute)의 수. 사람이 검증할 수 있는 유일한 값이라 화면 표기는 이걸 쓴다 */
  watchScore: number
  /** 단위 없는 내부 총점(가중치 튜닝 대상). 절대값은 의미가 없어 상대 비교에만 쓴다 */
  totalScore: number
}

/** 화면에 보여줄 이름. 닉네임이 없으면 익명 별칭으로 폴백한다. */
export function viewerDisplayName(viewer: TopViewer): string {
  return viewer.nickname ?? viewer.viewerAlias
}
