package com.example.laive.common.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "spring.ai.model",
        name = "chat",
        havingValue = "openai"
)
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
