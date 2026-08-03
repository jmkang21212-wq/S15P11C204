package com.example.laive.platform.oauth;

import java.util.List;

/**
 * 치지직 방송 설정 조회(/open/v1/lives/setting) 응답이다.
 * 이번 기능은 제목만 다루지만 응답 전체를 담아 카테고리·태그 확장에 대비한다.
 */
public record ChzzkLiveSetting(
        String defaultLiveTitle,
        ChzzkLiveCategory category,
        List<String> tags
) {

    public record ChzzkLiveCategory(
            String categoryType,
            String categoryId,
            String categoryValue,
            String posterImageUrl
    ) {
    }
}
