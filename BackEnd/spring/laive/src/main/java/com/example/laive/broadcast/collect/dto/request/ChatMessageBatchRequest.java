package com.example.laive.broadcast.collect.dto.request;

import com.example.laive.broadcast.collect.domain.ChatMessageItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * 채팅 메시지 배치 저장 요청이다(명세 13.3.1).
 */
public record ChatMessageBatchRequest(
        @NotEmpty List<@Valid Item> items
) {

    /**
     * @param viewerHash 수집기가 해시한 시청자 식별자. viewer_profiles.viewer_hash는 VARCHAR(128)이다
     * @param message    채팅 원문
     * @param sentAt     치지직 {@code messageTime}에서 변환한 시각
     */
    public record Item(
            @NotNull @Size(max = 128) String viewerHash,
            // @NotBlank가 아닌 이유: 공백만 있는 채팅 한 건 때문에 배치 전체가
            // 400으로 거절되면 정상 채팅까지 유실된다. DB 제약도 NOT NULL이지
            // 공백 금지가 아니다.
            @NotNull String message,
            @NotNull Instant sentAt
    ) {

        ChatMessageItem toDomain() {
            return new ChatMessageItem(viewerHash, message, sentAt);
        }
    }

    public List<ChatMessageItem> toDomain() {
        return items.stream().map(Item::toDomain).toList();
    }
}
