package com.example.laive.platform.core;

import java.util.List;

/**
 * 플랫폼 방송 설정 조회 결과다. 이번 기능은 제목만 변경하지만
 * 조회 응답에 함께 오는 카테고리·태그도 담아 확장에 대비한다.
 */
public record PlatformLiveSetting(
        String defaultLiveTitle,
        Category category,
        List<String> tags
) {

    public record Category(
            String categoryType,
            String categoryId,
            String categoryValue,
            String posterImageUrl
    ) {
    }
}
