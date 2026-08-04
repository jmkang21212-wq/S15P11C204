package com.example.laive.broadcast.realtime.domain;

import java.util.Map;

/**
 * chat-ai가 계산한 최근 윈도우(기본 180초)의 여론 지표다.
 *
 * <p>DB에는 저장하지 않는다(V8에서 sentiment_snapshots 폐기). 실시간 콘솔이
 * 현재값을 보여주고 재연결 복구를 위해 최신 1건만 Redis 스냅샷으로 남긴다(명세 10.6).
 *
 * <p>라벨은 이미 ASCII 코드로 변환된 뒤다. {@code analyzedChat}이 0이면 chat-ai는
 * 전 라벨 0.0인 분포를 주지만, 명세 10.6은 이때 dominant와 distribution을 null로
 * 요구하므로 생성 지점에서 null로 눌러 만든다.
 *
 * @param windowSec    윈도우 길이(초)
 * @param analyzedChat 윈도우 안에서 분석된 채팅 수 (chat-ai 응답의 chatCount)
 * @param engagement   유니크 채터 ÷ 시청자. 시청자를 알 수 없으면 null
 * @param dominant     최다 감정 코드. analyzedChat이 0이면 null
 * @param distribution 감정 코드 → 비율(합 1.0). analyzedChat이 0이면 null
 */
public record SentimentWindow(
        int windowSec,
        long analyzedChat,
        Double engagement,
        String dominant,
        Map<String, Double> distribution
) {
}
