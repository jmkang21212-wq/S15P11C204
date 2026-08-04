package com.example.laive.growth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "laive.growth.ai")
public record GrowthAiProperties(
        boolean enabled,
        String promptVersion,
        int maxInputChars,
        int maxOutputTokens,
        int maxActions,
        Duration timeout
) {
    public GrowthAiProperties {
        promptVersion = promptVersion == null || promptVersion.isBlank()
                ? "growth-action-v1" : promptVersion;
        maxInputChars = maxInputChars <= 0 ? 16000 : maxInputChars;
        maxOutputTokens = maxOutputTokens <= 0 ? 1000 : maxOutputTokens;
        maxActions = maxActions <= 0 ? 3 : maxActions;
        timeout = timeout == null ? Duration.ofSeconds(20) : timeout;
    }
}
