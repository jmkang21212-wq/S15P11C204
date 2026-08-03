package com.example.laive.platform.core;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 외부 방송 플랫폼의 확장 가능한 식별자다.
 *
 * <p>새 플랫폼을 추가할 때 코어 enum을 수정하지 않도록 값 객체로 관리한다.</p>
 */
public record PlatformCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z0-9_]{2,30}");

    public PlatformCode {
        Objects.requireNonNull(value, "platform code must not be null");
        value = value.trim().toUpperCase(Locale.ROOT);
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid platform code: " + value);
        }
    }

    public static PlatformCode of(String value) {
        return new PlatformCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
