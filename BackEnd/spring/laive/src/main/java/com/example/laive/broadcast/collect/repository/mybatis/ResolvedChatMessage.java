package com.example.laive.broadcast.collect.repository.mybatis;

import java.time.Instant;

/**
 * viewerHash를 viewer_profiles.id로 바꾼 뒤의 채팅 한 건이다.
 *
 * @param sentimentLabel 감정 코드. 분류하지 못한 건 null로 남는다
 */
public record ResolvedChatMessage(long viewerId, String message, Instant sentAt, String sentimentLabel) {
}
