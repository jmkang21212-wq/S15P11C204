package com.example.laive.platform.api;

import com.example.laive.platform.core.PlatformDescriptor;

import java.util.Set;

/** 지원 플랫폼과 Capability를 동적으로 렌더링하기 위한 공개 응답이다. */
public record PlatformDescriptorResponse(
        String platformCode,
        String displayName,
        boolean enabled,
        boolean oauthSupported,
        Set<String> capabilities,
        Integer maxAccountsPerStreamer
) {
    public static PlatformDescriptorResponse from(PlatformDescriptor descriptor) {
        return new PlatformDescriptorResponse(
                descriptor.platformCode().value(),
                descriptor.displayName(),
                descriptor.enabled(),
                descriptor.supports("OAUTH"),
                descriptor.capabilities(),
                descriptor.maxAccountsPerStreamer()
        );
    }
}
