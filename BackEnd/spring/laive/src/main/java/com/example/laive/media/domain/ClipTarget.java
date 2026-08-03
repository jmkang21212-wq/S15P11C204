package com.example.laive.media.domain;

/**
 * 업로드 대상 하이라이트의 현재 상태다.
 *
 * <p>이미 {@code UPLOADED}인데 같은 업로드가 다시 오는 것은 정상 흐름이다 —
 * 저장은 됐는데 응답을 못 받고 앱이 꺼진 경우다. 그래서 상태와 기존 경로를 함께 읽어
 * 재업로드에 기존 URL로 답할 수 있게 한다.
 */
public record ClipTarget(
        long highlightId,
        long sessionId,
        ClipStatus clipStatus,
        String clipUrl,
        String thumbnailUrl
) {
}
