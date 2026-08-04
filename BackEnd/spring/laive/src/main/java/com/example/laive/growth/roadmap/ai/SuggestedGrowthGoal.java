package com.example.laive.growth.roadmap.ai;

import com.example.laive.growth.ai.GeneratedGrowthAction;
import com.example.laive.growth.domain.GrowthMetricType;
import com.example.laive.growth.domain.GrowthMetricUnit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SuggestedGrowthGoal(
        GrowthMetricType metricType,
        BigDecimal targetValue,
        GrowthMetricUnit unit,
        LocalDate periodStart,
        LocalDate periodEnd,
        String title,
        String rationale,
        List<GeneratedGrowthAction> actions
) {
    /** 시작일을 포함해 센다. 4주 = 28일, 12주 = 84일이므로 종료일은 시작일 + 27~83일이다. */
    private static final int MIN_PERIOD_DAYS = 28;
    private static final int MAX_PERIOD_DAYS = 84;

    // 조건별로 나눠 던진다. 하나로 묶으면 어디가 틀렸는지 로그만 보고는 알 수 없다.
    public SuggestedGrowthGoal {
        actions = actions == null ? List.of() : List.copyOf(actions);
        if (metricType == null || unit == null || periodStart == null || periodEnd == null) {
            throw new IllegalArgumentException(
                    "Suggestion is missing a required field: metricType=%s unit=%s periodStart=%s periodEnd=%s"
                            .formatted(metricType, unit, periodStart, periodEnd)
            );
        }
        if (metricType.unit() != unit) {
            throw new IllegalArgumentException(
                    "%s expects unit %s but got %s."
                            .formatted(metricType, metricType.unit(), unit)
            );
        }
        if (targetValue == null || targetValue.signum() <= 0) {
            throw new IllegalArgumentException(
                    "%s: targetValue must be positive but was %s."
                            .formatted(metricType, targetValue)
            );
        }
        if (title == null || title.isBlank() || title.length() > 200) {
            throw new IllegalArgumentException(
                    "%s: title must be non-blank and at most 200 characters (length=%d)."
                            .formatted(metricType, title == null ? 0 : title.length())
            );
        }
        if (rationale == null || rationale.isBlank() || rationale.length() > 2000) {
            throw new IllegalArgumentException(
                    "%s: rationale must be non-blank and at most 2000 characters (length=%d)."
                            .formatted(metricType, rationale == null ? 0 : rationale.length())
            );
        }
        if (actions.isEmpty() || actions.size() > 3) {
            throw new IllegalArgumentException(
                    "%s: actions must be 1-3 but was %d.".formatted(metricType, actions.size())
            );
        }
        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException(
                    "%s: periodEnd %s precedes periodStart %s."
                            .formatted(metricType, periodEnd, periodStart)
            );
        }
        // 기간은 모델의 의도가 아니라 서버가 정하는 가드레일이다. 하루 넘겼다고 제안 3개를
        // 통째로 버리면 LLM 호출만 8번 태우고 OUTBOX_DEAD로 끝나므로, 범위 밖이면 잘라서 살린다.
        LocalDate earliest = periodStart.plusDays(MIN_PERIOD_DAYS - 1);
        LocalDate latest = periodStart.plusDays(MAX_PERIOD_DAYS - 1);
        if (periodEnd.isBefore(earliest)) {
            periodEnd = earliest;
        } else if (periodEnd.isAfter(latest)) {
            periodEnd = latest;
        }
    }
}
