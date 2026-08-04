package com.example.laive.common.ai;

public interface StructuredAiClient {
    <T> T generate(String systemPrompt, String userPrompt, Class<T> responseType);
}
