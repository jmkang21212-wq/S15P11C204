package com.example.laive.platform.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformCodeTest {

    @Test
    void normalizesExternalPlatformCode() {
        assertThat(PlatformCode.of("  youtube ").value()).isEqualTo("YOUTUBE");
    }

    @Test
    void rejectsCodesThatCannotBeUsedAsCatalogKeys() {
        assertThatThrownBy(() -> PlatformCode.of("you-tube"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlatformCode.of("x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
