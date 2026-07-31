package com.example.laive.common.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(ChatClient.class)
public class SpringAiStructuredAiClient implements StructuredAiClient {
    private final ChatClient chatClient;

    public SpringAiStructuredAiClient(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public <T> T generate(String systemPrompt, String userPrompt, Class<T> responseType) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .entity(responseType, spec -> spec.validateSchema());
    }
}
