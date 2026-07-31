package com.example.laive.growth.roadmap.ai;

import com.example.laive.common.ai.PromptResourceLoader;
import com.example.laive.common.ai.StructuredAiClient;
import com.example.laive.growth.roadmap.config.GrowthRoadmapAiProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(
        prefix = "laive.growth.roadmap-ai",
        name = "enabled",
        havingValue = "true"
)
public class SpringAiGrowthRoadmapGenerator implements GrowthRoadmapGenerator {
    private final StructuredAiClient aiClient;
    private final GrowthRoadmapAiProperties properties;
    private final Clock clock;
    private final String systemPrompt;
    private final String userTemplate;

    public SpringAiGrowthRoadmapGenerator(
            StructuredAiClient aiClient,
            GrowthRoadmapAiProperties properties,
            Clock clock,
            @Value("classpath:prompts/growth-roadmap-system.st") Resource system,
            @Value("classpath:prompts/growth-roadmap-user.st") Resource user
    ) {
        this.aiClient = aiClient;
        this.properties = properties;
        this.clock = clock;
        this.systemPrompt = PromptResourceLoader.read(system);
        this.userTemplate = PromptResourceLoader.read(user);
    }

    @Override
    public GrowthRoadmapResult generate(GrowthRoadmapInput input) {
        try {
            String prompt = render(input);
            GrowthRoadmapResult result = aiClient.generate(
                    systemPrompt, prompt, GrowthRoadmapResult.class
            );
            if (result == null) {
                throw new GrowthRoadmapGenerationException(
                        "Growth roadmap AI returned an empty result.", true
                );
            }
            result.validateAgainst(input, LocalDate.now(clock));
            return result;
        } catch (GrowthRoadmapGenerationException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new GrowthRoadmapGenerationException(
                    "Growth roadmap result failed semantic validation.",
                    exception, true
            );
        } catch (Exception exception) {
            throw new GrowthRoadmapGenerationException(
                    "Spring AI growth roadmap generation failed.",
                    exception, isRetryable(exception)
            );
        }
    }

    String render(GrowthRoadmapInput input) {
        String portfolio = input.portfolio()
                .map(value -> """
                        title=%s
                        introduction=%s
                        platforms=%s
                        tags=%s
                        performance=avg:%d peak:%d weekly:%d total:%d chat:%d
                        featuredHighlights=%s
                        """.formatted(
                        value.title(), value.introduction(), value.platforms(), value.tags(),
                        value.avgViewers(), value.peakViewers(), value.broadcastsPerWeek(),
                        value.totalBroadcastCount(), value.totalChat(),
                        value.featuredHighlights()))
                .orElse("없음");
        String trends = input.weeklyTrends().stream()
                .map(String::valueOf).collect(Collectors.joining("\n"));
        String reports = input.recentReports().stream()
                .map(report -> abbreviate(report.toString(), 1_500))
                .collect(Collectors.joining("\n"));
        String goals = input.acceptedGoals().stream()
                .map(String::valueOf).collect(Collectors.joining("\n"));
        String previous = input.previousSuggestions().stream()
                .map(String::valueOf).collect(Collectors.joining("\n"));
        String prompt = userTemplate.formatted(
                LocalDate.now(clock), portfolio, trends, input.statistics(), reports,
                goals, previous, input.currentValues()
        );
        return prompt.length() <= properties.maxInputChars()
                ? prompt : prompt.substring(0, properties.maxInputChars());
    }

    private String abbreviate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private boolean isRetryable(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof RestClientResponseException response) {
                int status = response.getStatusCode().value();
                return status == 408 || status == 429 || status >= 500;
            }
            if (current.getClass().getSimpleName().contains("NonTransient")) {
                return false;
            }
        }
        return true;
    }
}
