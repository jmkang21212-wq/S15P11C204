package com.example.laive.broadcast.session.domain;

import java.math.BigDecimal;

/**
 * 방송 종료 시 PostgreSQL 원천 데이터로 확정한 통계다.
 *
 * 감정 비율 세 개는 소수점 세 자리로 반올림된 값이라 합이 1.000에서 ±0.001
 * 어긋날 수 있다. REPORTS의 세 컬럼은 NUMERIC(4,3)이라 더 정밀하게 저장할 수도
 * 없으므로, 화면의 비율 그래프가 닫히도록 생성 시점에 합을 1.000으로 맞춘다.
 * 잔차는 표본이 가장 큰 비율에 더해 상대 오차를 최소화한다.
 *
 * 정규화를 표준 생성자에 두는 이유는 이 record가 집계 쿼리 결과 매핑과 리포트
 * 저장에 그대로 쓰이기 때문이다. 별도 메서드로 두면 호출을 빠뜨린 경로에서
 * 합이 1이 아닌 값이 저장된다.
 */
public record SessionStatistics(
        Integer averageViewers,
        Integer peakViewers,
        long totalChat,
        BigDecimal positiveRatio,
        BigDecimal neutralRatio,
        BigDecimal negativeRatio
) {
    private static final BigDecimal RATIO_TOTAL = BigDecimal.ONE.setScale(3);

    public SessionStatistics {
        // 감정 분석 결과가 없는 세션은 세 비율이 함께 NULL이다 (명세 1.5).
        if (positiveRatio != null && neutralRatio != null && negativeRatio != null) {
            BigDecimal residual = RATIO_TOTAL.subtract(
                    positiveRatio.add(neutralRatio).add(negativeRatio)
            );
            if (residual.signum() != 0) {
                if (positiveRatio.compareTo(neutralRatio) >= 0
                        && positiveRatio.compareTo(negativeRatio) >= 0) {
                    positiveRatio = positiveRatio.add(residual);
                } else if (neutralRatio.compareTo(negativeRatio) >= 0) {
                    neutralRatio = neutralRatio.add(residual);
                } else {
                    negativeRatio = negativeRatio.add(residual);
                }
            }
        }
    }
}

