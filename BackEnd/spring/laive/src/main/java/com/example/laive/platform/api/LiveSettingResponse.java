package com.example.laive.platform.api;

import com.example.laive.platform.core.PlatformLiveSetting;

import java.util.List;

/**
 * 방송 설정 응답이다. 이번 기능은 제목만 변경하지만 조회에 함께 오는
 * 카테고리·태그도 읽기 전용으로 내려 확장에 대비한다. 변경 응답에서는 null일 수 있다.
 */
public record LiveSettingResponse(
        String defaultLiveTitle,
        CategoryResponse category,
        List<String> tags
) {

    public static LiveSettingResponse of(PlatformLiveSetting setting) {
        PlatformLiveSetting.Category category = setting.category();
        return new LiveSettingResponse(
                setting.defaultLiveTitle(),
                category == null ? null : new CategoryResponse(
                        category.categoryType(),
                        category.categoryId(),
                        category.categoryValue(),
                        category.posterImageUrl()
                ),
                setting.tags() == null ? null : List.copyOf(setting.tags())
        );
    }

    public record CategoryResponse(
            String categoryType,
            String categoryId,
            String categoryValue,
            String posterImageUrl
    ) {
    }
}
