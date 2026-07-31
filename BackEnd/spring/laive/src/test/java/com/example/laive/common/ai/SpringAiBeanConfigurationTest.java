package com.example.laive.common.ai;

import com.example.laive.growth.ai.GrowthActionGenerator;
import com.example.laive.growth.ai.SpringAiGrowthActionGenerator;
import com.example.laive.report.ai.ReportSummaryGenerator;
import com.example.laive.report.ai.SpringAiReportSummaryGenerator;
import com.example.laive.report.ai.SpringAiTriggerNarrativeGenerator;
import com.example.laive.report.ai.TriggerNarrativeGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.ai.model.chat=openai",
        "spring.ai.openai.api-key=test-api-key",
        "laive.report.ai.enabled=true",
        "laive.growth.ai.enabled=true",
        "laive.auth.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "laive.platform.token-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE="
})
class SpringAiBeanConfigurationTest {
    @Autowired
    private ChatClient chatClient;
    @Autowired
    private StructuredAiClient structuredAiClient;
    @Autowired
    private TriggerNarrativeGenerator triggerGenerator;
    @Autowired
    private ReportSummaryGenerator summaryGenerator;
    @Autowired
    private GrowthActionGenerator growthGenerator;

    @Test
    void createsRealAiBeansWhenOpenAiAndFeaturesAreEnabled() {
        assertThat(chatClient).isNotNull();
        assertThat(structuredAiClient).isInstanceOf(SpringAiStructuredAiClient.class);
        assertThat(triggerGenerator).isInstanceOf(SpringAiTriggerNarrativeGenerator.class);
        assertThat(summaryGenerator).isInstanceOf(SpringAiReportSummaryGenerator.class);
        assertThat(growthGenerator).isInstanceOf(SpringAiGrowthActionGenerator.class);
    }
}
