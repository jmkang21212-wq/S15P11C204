package com.example.laive.broadcast.collect.dto.request;

import com.example.laive.broadcast.collect.domain.ViewerMetricItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.List;

/**
 * 시청자 수 지표 배치 저장 요청이다(명세 13.3.2).
 */
public record ViewerMetricBatchRequest(
        @NotEmpty List<@Valid Item> items
) {

    public record Item(
            @NotNull Instant measuredAt,
            // viewer_metrics.ck_viewer_metrics_viewer_count와 같은 조건이다.
            @PositiveOrZero int viewerCount
    ) {

        ViewerMetricItem toDomain() {
            return new ViewerMetricItem(measuredAt, viewerCount);
        }
    }

    public List<ViewerMetricItem> toDomain() {
        return items.stream().map(Item::toDomain).toList();
    }
}
