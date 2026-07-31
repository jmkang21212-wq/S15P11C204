package com.example.laive.growth.roadmap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "laive.growth.roadmap-ai")
public record GrowthRoadmapAiProperties(
        boolean enabled,
        String promptVersion,
        int maxInputChars,
        Duration timeout,
        Duration refreshCooldown
) {
    public GrowthRoadmapAiProperties {
        promptVersion = promptVersion == null || promptVersion.isBlank()
                ? "growth-roadmap-v1" : promptVersion;
        maxInputChars = maxInputChars <= 0 ? 24_000 : maxInputChars;
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        refreshCooldown = refreshCooldown == null ? Duration.ofDays(7) : refreshCooldown;
    }
}
