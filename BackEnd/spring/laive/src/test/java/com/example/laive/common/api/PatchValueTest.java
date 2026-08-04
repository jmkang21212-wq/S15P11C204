package com.example.laive.common.api;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PATCH 부분 수정의 3가지 입력(미전송, 명시적 null, 값)이 모두 구분되는지 검증한다.
 *
 * Spring Boot 4의 웹 계층은 Jackson 3(tools.jackson)을 사용하므로 같은 Mapper로 검증한다.
 */
class PatchValueTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    private record SamplePatch(
            PatchValue<Long> platformAccountId,
            PatchValue<String> memo,
            PatchValue<Instant> startAt
    ) {
    }

    @Test
    void 미전송_필드는_PatchValue자체가_null이라_현재값을_유지한다() {
        SamplePatch patch = objectMapper.readValue("{}", SamplePatch.class);

        assertThat(patch.memo()).isNull();
        assertThat(PatchValue.resolve(patch.memo(), "기존 메모")).isEqualTo("기존 메모");
        assertThat(PatchValue.changes(patch.memo(), "기존 메모")).isFalse();
    }

    @Test
    void 명시적_null은_값을_비우라는_뜻이다() {
        SamplePatch patch = objectMapper.readValue("{\"memo\":null}", SamplePatch.class);

        assertThat(patch.memo()).isNotNull();
        assertThat(patch.memo().value()).isNull();
        assertThat(PatchValue.resolve(patch.memo(), "기존 메모")).isNull();
        assertThat(PatchValue.changes(patch.memo(), "기존 메모")).isTrue();
    }

    @Test
    void 값이_있으면_선언한_제네릭_타입으로_역직렬화된다() {
        SamplePatch patch = objectMapper.readValue(
                "{\"platformAccountId\":15,\"memo\":\"새 메모\",\"startAt\":\"2026-07-20T12:00:00Z\"}",
                SamplePatch.class
        );

        assertThat(patch.platformAccountId().value()).isEqualTo(15L);
        assertThat(patch.memo().value()).isEqualTo("새 메모");
        assertThat(patch.startAt().value()).isEqualTo(Instant.parse("2026-07-20T12:00:00Z"));
    }

    @Test
    void 같은_값으로_보내면_변경으로_보지_않는다() {
        SamplePatch patch = objectMapper.readValue("{\"platformAccountId\":15}", SamplePatch.class);

        assertThat(PatchValue.changes(patch.platformAccountId(), 15L)).isFalse();
        assertThat(PatchValue.changes(patch.platformAccountId(), 16L)).isTrue();
    }
}
