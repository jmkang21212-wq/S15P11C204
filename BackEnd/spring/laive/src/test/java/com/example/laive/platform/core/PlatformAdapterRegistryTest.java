package com.example.laive.platform.core;

import com.example.laive.platform.exception.PlatformException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformAdapterRegistryTest {

    @Test
    void resolvesEnabledAdapterWithoutCoreEnumChanges() {
        PlatformIntegrationAdapter fake = adapter("FAKE_PLATFORM", true);
        PlatformAdapterRegistry registry = new PlatformAdapterRegistry(List.of(fake));

        assertThat(registry.require("fake_platform")).isSameAs(fake);
        assertThat(registry.enabledDescriptors())
                .extracting(descriptor -> descriptor.platformCode().value())
                .containsExactly("FAKE_PLATFORM");
    }

    @Test
    void disabledAdapterIsNotPubliclyUsable() {
        PlatformAdapterRegistry registry = new PlatformAdapterRegistry(
                List.of(adapter("SOOP", false)));

        assertThatThrownBy(() -> registry.require("SOOP"))
                .isInstanceOf(PlatformException.class)
                .extracting("code")
                .isEqualTo("PLATFORM_INVALID_REQUEST");
        assertThat(registry.enabledDescriptors()).isEmpty();
    }

    @Test
    void rejectsDuplicateAdapterRegistrationAtStartup() {
        assertThatThrownBy(() -> new PlatformAdapterRegistry(List.of(
                adapter("YOUTUBE", true), adapter("youtube", true))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate platform adapter");
    }

    private PlatformIntegrationAdapter adapter(String code, boolean enabled) {
        PlatformCode platformCode = PlatformCode.of(code);
        return new PlatformIntegrationAdapter() {
            @Override
            public PlatformCode platformCode() {
                return platformCode;
            }

            @Override
            public PlatformDescriptor descriptor() {
                return new PlatformDescriptor(
                        platformCode, platformCode.value(), enabled,
                        Set.of(PlatformCapabilityCode.CHAT_COLLECTION), null);
            }
        };
    }
}
