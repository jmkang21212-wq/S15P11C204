package com.example.laive.broadcast.fandom.config;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 제외 닉네임 정규화다. SQL의 {@code lower(COALESCE(nickname, ''))}와 짝이 맞아야
 * 한다 — 여기서 빈 값을 안 걸러내면 닉네임 없는 시청자가 목록에서 통째로 사라진다.
 */
class FandomPropertiesTest {

    /**
     * 환경변수 미설정({@code FANDOM_EXCLUDED_NICKNAMES:})의 실제 바인딩 결과가
     * 빈 문자열 하나짜리 리스트다. 목록이 비어야 SQL의 필터 자체가 붙지 않는다.
     */
    @Test
    void 빈_문자열만_있으면_제외_목록은_비어_있다() {
        assertThat(new FandomProperties(List.of("")).excludedNicknames()).isEmpty();
        assertThat(new FandomProperties(null).excludedNicknames()).isEmpty();
    }

    @Test
    void 공백과_대소문자와_중복을_접는다() {
        FandomProperties properties =
                new FandomProperties(List.of(" 팀원닉 ", "TeamMate", "teammate", "  "));

        assertThat(properties.excludedNicknames())
                .containsExactly("팀원닉", "teammate");
    }

    @Test
    void null_원소가_섞여도_터지지_않는다() {
        assertThat(new FandomProperties(Arrays.asList("팀원닉", null)).excludedNicknames())
                .containsExactly("팀원닉");
    }
}
