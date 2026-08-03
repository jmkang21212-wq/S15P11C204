package com.example.laive.growth.ai;

import com.example.laive.common.ai.PromptResourceLoader;
import com.example.laive.common.ai.StructuredAiClient;
import com.example.laive.growth.config.GrowthAiProperties;
import com.example.laive.growth.exception.GrowthActionGenerationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "laive.growth.ai", name = "enabled", havingValue = "true")
public class SpringAiGrowthActionGenerator implements GrowthActionGenerator {
    private final StructuredAiClient aiClient;
    private final GrowthAiProperties properties;
    private final String systemPrompt;
    private final String userTemplate;

    public SpringAiGrowthActionGenerator(
            StructuredAiClient aiClient,
            GrowthAiProperties properties,
            @Value("classpath:prompts/growth-action-system.st") Resource system,
            @Value("classpath:prompts/growth-action-user.st") Resource user
    ) {
        this.aiClient = aiClient;
        this.properties = properties;
        this.systemPrompt = PromptResourceLoader.read(system);
        this.userTemplate = PromptResourceLoader.read(user);
    }

    @Override
    public GrowthActionResult generate(GrowthActionInput input) {
        try {
            String reports = input.recentReports().stream()
                    .map(report -> "%s | avg=%s peak=%s chat=%d minutes=%d | platforms=%s | %s".formatted(
                            report.endedAt(), report.avgViewers(), report.peakViewers(),
                            report.totalChat(), report.broadcastMinutes(),
                            abbreviate(report.platformBreakdown(), 500),
                            abbreviate(report.summary(), 500)
                    ))
                    .reduce("", (left, right) -> left + "\n" + right);
            String previous = input.previousActions().stream()
                    .map(action -> action.title() + ": " + abbreviate(action.content(), 500))
                    .reduce("", (left, right) -> left + "\n" + right);
            var goal = input.goal();
            String prompt = userTemplate.formatted(
                    goal.metricType(), goal.targetValue(), goal.unit(),
                    input.currentValue(), input.progressRate(),
                    goal.periodStart(), goal.periodEnd(), reports, previous
            );
            if (prompt.length() > properties.maxInputChars()) {
                prompt = prompt.substring(0, properties.maxInputChars());
            }
            GrowthActionResult result = aiClient.generate(
                    systemPrompt, prompt, GrowthActionResult.class
            );
            if (result == null || result.actions().size() > properties.maxActions()) {
                throw new GrowthActionGenerationException("성장 액션 생성 결과가 올바르지 않습니다.");
            }
            return result;
        } catch (GrowthActionGenerationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new GrowthActionGenerationException("Spring AI 성장 액션 생성에 실패했습니다.", exception);
        }
    }

    private String abbreviate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
