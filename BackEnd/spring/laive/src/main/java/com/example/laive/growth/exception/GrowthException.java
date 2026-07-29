package com.example.laive.growth.exception;

import com.example.laive.common.exception.BusinessException;

public class GrowthException extends BusinessException {
    private GrowthException(GrowthErrorCode code) {
        super(code.status(), code.name(), code.message());
    }

    public static GrowthException goalNotFound() {
        return new GrowthException(GrowthErrorCode.GROWTH_GOAL_NOT_FOUND);
    }

    public static GrowthException invalidGoal() {
        return new GrowthException(GrowthErrorCode.INVALID_GROWTH_GOAL);
    }

    public static GrowthException stateConflict() {
        return new GrowthException(GrowthErrorCode.GROWTH_GOAL_STATE_CONFLICT);
    }

    public static GrowthException invalidPeriod() {
        return new GrowthException(GrowthErrorCode.INVALID_GROWTH_PERIOD);
    }
}
