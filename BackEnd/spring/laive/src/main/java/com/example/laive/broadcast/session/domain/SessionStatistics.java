package com.example.laive.broadcast.session.domain;

/**
 * 방송 종료 시 PostgreSQL 원천 데이터로 확정한 통계다.
 *
 * 감정은 비율이 아니라 라벨별 건수로 담는다(API 1.6). 비율을 소수점 세 자리로
 * 반올림해 저장하면 8개의 합이 1에서 벌어지는데, 건수는 나눗셈이 조회 시점에
 * 일어나므로 그 문제가 없다. 여러 세션을 묶는 성장 리포트에서도 건수를 합해
 * 나누면 채팅량 가중이 자동으로 걸린다.
 *
 * 여덟 건수와 {@code analyzedChat}은 집계 쿼리의 {@code count(...)} 결과라 항상
 * 값이 있다. 반면 {@code averageViewers}와 {@code peakViewers}는 시청자 지표가
 * 한 건도 없으면 NULL이다.
 */
public record SessionStatistics(
        Integer averageViewers,
        Integer peakViewers,
        long totalChat,
        long analyzedChat,
        long joyCount,
        long excitementCount,
        long supportCount,
        long surpriseCount,
        long sadnessCount,
        long angerCount,
        long confusionCount,
        long neutralCount
) {
}
