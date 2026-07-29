package com.example.laive.growth.domain;

public enum GrowthMetricType {
    AVG_VIEWERS(GrowthMetricUnit.VIEWERS),
    BROADCASTS_PER_WEEK(GrowthMetricUnit.COUNT),
    TOTAL_CHAT(GrowthMetricUnit.MESSAGES),
    BROADCAST_MINUTES(GrowthMetricUnit.MINUTES);

    private final GrowthMetricUnit unit;

    GrowthMetricType(GrowthMetricUnit unit) {
        this.unit = unit;
    }

    public GrowthMetricUnit unit() {
        return unit;
    }
}
