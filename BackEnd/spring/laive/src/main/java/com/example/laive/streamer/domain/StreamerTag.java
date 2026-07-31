package com.example.laive.streamer.domain;

/**
 * 스트리머에게 붙는 카탈로그 태그 하나다 (API 6.6, 12.1).
 *
 * <p>태그는 사용자가 만드는 값이 아니라 V14 시드가 채운 고정 카탈로그에서 고른다.
 * 프로필(6.6)과 포트폴리오(12.1) 응답이 같은 형태를 쓰므로 도메인 한 곳에 둔다.
 */
public record StreamerTag(
        long tagId,
        String name,
        String tagType
) {
}
