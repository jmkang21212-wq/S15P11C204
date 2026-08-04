package com.example.laive.growth.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrowthActionResultTest {
    @Test
    void acceptsOneToThreeUniqueActions() {
        var result = new GrowthActionResult(List.of(
                new GeneratedGrowthAction("오프닝 질문 고정", "방송 시작 10분 안에 질문하고 채팅 수를 확인합니다."),
                new GeneratedGrowthAction("반응 구간 재현", "다음 방송 중간에 같은 형식을 적용하고 평균 시청자를 확인합니다.")
        ));

        assertThat(result.actions()).hasSize(2);
    }

    @Test
    void rejectsDuplicateTitlesAndInvalidCount() {
        assertThatThrownBy(() -> new GrowthActionResult(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GrowthActionResult(List.of(
                new GeneratedGrowthAction("같은 제목", "첫 번째 내용"),
                new GeneratedGrowthAction("같은 제목", "두 번째 내용")
        ))).isInstanceOf(IllegalArgumentException.class);
    }
}
