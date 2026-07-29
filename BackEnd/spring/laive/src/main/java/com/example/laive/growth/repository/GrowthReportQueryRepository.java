package com.example.laive.growth.repository;

import com.example.laive.growth.domain.GrowthGoalMetrics;
import com.example.laive.growth.domain.GrowthPeriod;
import com.example.laive.growth.domain.GrowthPeriodType;
import com.example.laive.growth.domain.GrowthSeriesPoint;

import java.util.List;

public interface GrowthReportQueryRepository {
    List<GrowthSeriesPoint> findSeries(
            long streamerId,
            GrowthPeriodType periodType,
            GrowthPeriod period
    );

    List<GrowthGoalMetrics> findGoalMetrics(long streamerId, List<Long> goalIds);
}
