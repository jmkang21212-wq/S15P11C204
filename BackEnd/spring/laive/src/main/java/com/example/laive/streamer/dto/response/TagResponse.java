package com.example.laive.streamer.dto.response;

import com.example.laive.streamer.domain.StreamerTag;

/**
 * 태그 한 개의 응답이다 (API 6.6, 12.1의 tags[] 원소이자 GET /tags의 원소).
 *
 * <p>필드명은 명세·프론트 타입(types/portfolio.ts PortfolioTag)과 일치한다.
 */
public record TagResponse(
        long tagId,
        String name,
        String tagType
) {
    public static TagResponse from(StreamerTag tag) {
        return new TagResponse(tag.tagId(), tag.name(), tag.tagType());
    }
}
