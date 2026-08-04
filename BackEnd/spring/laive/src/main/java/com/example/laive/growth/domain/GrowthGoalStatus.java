package com.example.laive.growth.domain;

public enum GrowthGoalStatus {
    ACTIVE,
    ACHIEVED,
    CANCELLED;

    public boolean canTransitionTo(GrowthGoalStatus next) {
        if (this == CANCELLED) {
            return next == CANCELLED;
        }
        return next != null;
    }
}
