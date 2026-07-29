package com.example.laive.common.api;

import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * PATCH 요청에서 "필드 미전송"과 "명시적 null"을 구분한다.
 *
 * 필드를 아예 보내지 않으면 PatchValue 자체가 null이라 "변경 없음"이고,
 * {@code "memo": null}을 보내면 PatchValue(null)이라 "값을 비움"이 된다.
 * 두 경우를 구분하지 못하면 nullable 컬럼을 다시 null로 되돌릴 방법이 없다.
 */
@JsonDeserialize(using = PatchValueDeserializer.class)
public record PatchValue<T>(T value) {

    public static <T> PatchValue<T> of(T value) {
        return new PatchValue<>(value);
    }

    /** 미전송이면 현재 값을 유지하고, 전송됐으면 보낸 값으로 바꾼다. */
    public static <T> T resolve(PatchValue<T> patch, T current) {
        return patch == null ? current : patch.value();
    }

    /** 값을 실제로 바꾸는 요청인지 판단한다. 수정 잠금 규칙 검사에 사용한다. */
    public static <T> boolean changes(PatchValue<T> patch, T current) {
        if (patch == null) {
            return false;
        }
        return !java.util.Objects.equals(patch.value(), current);
    }
}
