import type { MetricType } from '@renderer/types/growth'

/**
 * 성장 지표의 한글 이름과 단위.
 *
 * 목표 카드(채택된 목표)와 추천 카드가 같은 지표를 그리므로 한 곳에 둔다. 두 곳에 복사해 두면
 * 한쪽만 고쳐 같은 지표가 다른 이름으로 보인다. `components/common/`이 아니라 이 폴더에 두는
 * 이유는 성장 리포트 밖에서 쓰이지 않기 때문이다.
 */
export const METRIC_LABEL: Record<MetricType, string> = {
  AVG_VIEWERS: '평균 시청자',
  BROADCASTS_PER_WEEK: '주당 방송 횟수',
  TOTAL_CHAT: '총 채팅',
  BROADCAST_MINUTES: '방송 시간'
}

export const METRIC_UNIT: Record<MetricType, string> = {
  AVG_VIEWERS: '명',
  BROADCASTS_PER_WEEK: '회',
  TOTAL_CHAT: '개',
  BROADCAST_MINUTES: '분'
}
