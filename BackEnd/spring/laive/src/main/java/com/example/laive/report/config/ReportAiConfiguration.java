package com.example.laive.report.config;

import com.example.laive.report.ai.ReportSummaryGenerator;
import com.example.laive.report.ai.TriggerNarrativeGenerator;
import com.example.laive.report.exception.SummaryGenerationException;
import com.example.laive.report.exception.TriggerAnalysisException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableConfigurationProperties(ReportAiProperties.class)
public class ReportAiConfiguration {
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "spring.ai.model",
            name = "chat",
            havingValue = "openai"
    )
    ChatClient reportChatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean(name = "analysisAiExecutor")
    Executor analysisAiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("analysis-ai-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "laive.report.ai",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true
    )
    TriggerNarrativeGenerator unavailableTriggerGenerator() {
        return input -> {
            throw new TriggerAnalysisException("리포트 AI가 비활성화되어 있습니다.");
        };
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "laive.report.ai",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true
    )
    ReportSummaryGenerator unavailableSummaryGenerator() {
        return input -> {
            throw new SummaryGenerationException("리포트 AI가 비활성화되어 있습니다.");
        };
    }
}
