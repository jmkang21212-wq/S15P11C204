package com.example.laive.streamer.risk.domain;

/**
 * 개인 사전 한 항목이다 (streamer_risk_terms 한 행).
 *
 * <p>{@code term}과 {@code display}가 따로 있는 이유는 정규화 때문이다. 매칭은 정규화된
 * 문자열 위에서 일어나므로 중복 판정도 그쪽으로 해야 하지만("씨발"과 "씨이이발"은 같은
 * 항목이다), 정규화 결과는 사람이 읽기 나빠서 설정 화면에 그대로 보여줄 수 없다.
 *
 * <p>record는 MyBatis가 생성자 순서로 매핑하므로 SELECT 컬럼 순서를 아래 순서와
 * 반드시 일치시킨다.
 */
public record StreamerRiskTerm(
        long id,
        RiskTermKind kind,
        String term,
        String display,
        /** BLOCK 전용. NULL이면 {@link RiskTermSeverity#DEFAULT}로 본다. */
        RiskTermSeverity severity
) {

    public RiskTermSeverity effectiveSeverity() {
        return RiskTermSeverity.orDefault(severity);
    }
}
