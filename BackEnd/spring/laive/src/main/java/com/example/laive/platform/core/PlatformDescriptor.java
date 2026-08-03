package com.example.laive.platform.core;

import java.util.Set;

/** 프론트와 코어 서비스가 사용하는 플랫폼의 공개 기능 명세다. */
public record PlatformDescriptor(
        PlatformCode platformCode,
        String displayName,
        boolean enabled,
        Set<String> capabilities,
        Integer maxAccountsPerStreamer
) {
    public PlatformDescriptor {
        capabilities = Set.copyOf(capabilities);
    }

    public boolean supports(String capability) {
        return capabilities.contains(capability);
    }
}
