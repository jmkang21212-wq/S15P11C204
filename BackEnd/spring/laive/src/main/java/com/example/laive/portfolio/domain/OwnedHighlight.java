package com.example.laive.portfolio.domain;

/**
 * 소유권 검증을 통과한 하이라이트다.
 *
 * <p>소유권은 highlights → reports → broadcast_sessions.streamer_id 경로로 판정한다.
 * 요청한 id 수보다 적게 돌아오면 남의 것이나 없는 것이 섞인 것이다.
 */
public record OwnedHighlight(
        long highlightId,
        String clipUrl,
        String thumbnailUrl
) {
}
