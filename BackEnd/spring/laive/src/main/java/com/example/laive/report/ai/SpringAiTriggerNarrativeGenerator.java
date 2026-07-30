package com.example.laive.report.ai;

import com.example.laive.report.domain.SentimentDistribution;
import com.example.laive.report.exception.TriggerAnalysisException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(prefix = "laive.report.ai", name = "enabled", havingValue = "true")
public class SpringAiTriggerNarrativeGenerator implements TriggerNarrativeGenerator {
    private final ChatClient chatClient;
    private final ReportInputCompressor compressor;
    private final String systemPrompt;
    private final String userTemplate;

    public SpringAiTriggerNarrativeGenerator(
            ChatClient chatClient,
            ReportInputCompressor compressor,
            @Value("classpath:prompts/report-trigger-system.st") Resource system,
            @Value("classpath:prompts/report-trigger-user.st") Resource user
    ) {
        this.chatClient = chatClient;
        this.compressor = compressor;
        this.systemPrompt = read(system);
        this.userTemplate = read(user);
    }

    @Override
    public TriggerNarrative generate(TriggerAnalysisInput input) {
        try {
            var trigger = input.trigger();
            String userPrompt = userTemplate.formatted(
                    input.report().title(), input.report().category(), input.report().durationMinutes(),
                    trigger.triggerType(), trigger.startMs(), trigger.endMs(), trigger.peakZ(),
                    trigger.peakEngagement(), trigger.avgViewers(), trigger.chatCount(),
                    trigger.uniqueChatters(),
                    SentimentDistribution.from(trigger.sentimentCounts()).distribution(),
                    compressor.transcripts(input.transcripts()),
                    compressor.chats(input.chats()),
                    compressor.risks(input.risks())
            );
            TriggerNarrative result = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .entity(TriggerNarrative.class, spec -> spec.validateSchema());
            if (result == null) {
                throw new TriggerAnalysisException("트리거 분석 결과가 비어 있습니다.");
            }
            return result;
        } catch (TriggerAnalysisException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new TriggerAnalysisException("Spring AI 트리거 분석에 실패했습니다.", exception);
        }
    }

    private static String read(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("리포트 프롬프트를 읽을 수 없습니다.", exception);
        }
    }
}
