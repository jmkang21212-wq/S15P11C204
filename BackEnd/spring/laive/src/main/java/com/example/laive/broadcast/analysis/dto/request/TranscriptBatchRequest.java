package com.example.laive.broadcast.analysis.dto.request;

import com.example.laive.broadcast.analysis.domain.TranscriptItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * AI 서버가 보내는 STT 전사 배치다(명세 13.4).
 */
public record TranscriptBatchRequest(
        @NotEmpty(message = "비어 있을 수 없습니다.")
        List<@Valid Item> items
) {
    public record Item(
            @NotBlank(message = "비어 있을 수 없습니다.")
            String text,

            @NotNull(message = "필수 값입니다.")
            Instant startedAt,

            @NotNull(message = "필수 값입니다.")
            Instant endedAt
    ) {
        public TranscriptItem toDomain() {
            return new TranscriptItem(text, startedAt, endedAt);
        }
    }

    public List<TranscriptItem> toDomain() {
        return items.stream().map(Item::toDomain).toList();
    }
}
