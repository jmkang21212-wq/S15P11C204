package com.example.laive.growth.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class GrowthMath {
    private GrowthMath() {
    }

    public static BigDecimal changeRate(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous)
                .divide(previous, 4, RoundingMode.HALF_UP);
    }

    public static BigDecimal progress(BigDecimal current, BigDecimal target) {
        if (current == null) {
            return null;
        }
        return current.divide(target, 4, RoundingMode.HALF_UP);
    }
}
