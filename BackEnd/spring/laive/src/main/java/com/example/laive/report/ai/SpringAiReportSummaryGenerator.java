package com.example.laive.report.ai;

import com.example.laive.report.domain.SentimentDistribution;
import com.example.laive.report.exception.SummaryGenerationException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(prefix = "laive.report.ai", name = "enabled", havingValue = "true")
public class SpringAiReportSummaryGenerator implements ReportSummaryGenerator {
    private final ChatClient chatClient;
    private final ReportInputCompressor compressor;
    private final String systemPrompt;
    private final String userTemplate;

    public SpringAiReportSummaryGenerator(
            ChatClient chatClient,
            ReportInputCompressor compressor,
            @Value("classpath:prompts/report-summary-system.st") Resource system,
            @Value("classpath:prompts/report-summary-user.st") Resource user
    ) {
        this.chatClient = chatClient;
        this.compressor = compressor;
        this.systemPrompt = read(system);
        this.userTemplate = read(user);
    }

    @Override
    public ReportAiResult generate(ReportSummaryInput input) {
        try {
            var report = input.report().report();
            String triggerText = input.triggers().stream()
                    .map(trigger -> "%d-%dms %s: %s".formatted(
                            trigger.startMs(), trigger.endMs(), trigger.triggerType(),
                            trigger.narrative() == null ? "분석 실패" : trigger.narrative()
                    ))
                    .reduce("", (left, right) -> left + "\n" + right);
            String userPrompt = userTemplate.formatted(
                    input.report().title(), input.report().category(), input.report().durationMinutes(),
                    report.avgViewers() == null ? 0 : report.avgViewers(),
                    report.peakViewers() == null ? 0 : report.peakViewers(),
                    report.totalChat(), report.sentimentCounts().analyzedChat(),
                    SentimentDistribution.from(report.sentimentCounts()).distribution(),
                    triggerText, compressor.risks(input.risks()),
                    compressor.transcripts(input.transcripts()),
                    input.report().hasCollectionGap()
            );
            ReportAiResult result = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .entity(ReportAiResult.class, spec -> spec.validateSchema());
            if (result == null) {
                throw new SummaryGenerationException("리포트 요약 결과가 비어 있습니다.");
            }
            return result;
        } catch (SummaryGenerationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SummaryGenerationException("Spring AI 리포트 요약에 실패했습니다.", exception);
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
