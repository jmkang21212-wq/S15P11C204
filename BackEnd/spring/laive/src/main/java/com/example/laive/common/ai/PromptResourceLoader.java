package com.example.laive.common.ai;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class PromptResourceLoader {
    private PromptResourceLoader() {
    }

    public static String read(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("AI prompt resource could not be read.", exception);
        }
    }
}
