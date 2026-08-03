package com.example.laive.broadcast.session.dto.request;

import com.example.laive.broadcast.session.domain.StartBroadcastSessionCommand;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

import java.util.LinkedHashSet;
import java.util.List;

/** 단일 필드 하위 호환과 다중 플랫폼 요청을 함께 지원한다. */
public record StartBroadcastSessionRequest(
        @Positive Long platformAccountId,
        @Size(min = 1, max = 10) List<@Positive Long> platformAccountIds,
        @Positive Long calendarEventId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 50) String category,
        @Size(max = 10)
        @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$") String language
) {
    @AssertTrue(message = "platformAccountId 또는 platformAccountIds 중 하나만 입력해야 합니다.")
    public boolean isPlatformSelectionValid() {
        boolean legacy = platformAccountId != null;
        boolean multi = platformAccountIds != null && !platformAccountIds.isEmpty();
        return legacy ^ multi;
    }

    @AssertTrue(message = "platformAccountIds must not contain duplicates")
    public boolean isPlatformSelectionDistinct() {
        return platformAccountIds == null
                || new LinkedHashSet<>(platformAccountIds).size() == platformAccountIds.size();
    }

    public StartBroadcastSessionCommand toCommand() {
        List<Long> selected = platformAccountId != null
                ? List.of(platformAccountId)
                : List.copyOf(new LinkedHashSet<>(platformAccountIds));
        return new StartBroadcastSessionCommand(selected, calendarEventId, title, category, language);
    }
}
