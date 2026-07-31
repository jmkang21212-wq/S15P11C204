package com.example.laive.growth.roadmap.config;

import com.example.laive.growth.roadmap.ai.GrowthRoadmapGenerationException;
import com.example.laive.growth.roadmap.ai.GrowthRoadmapGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GrowthRoadmapAiProperties.class)
public class GrowthRoadmapAiConfiguration {
    @Bean
    @ConditionalOnProperty(
            prefix = "laive.growth.roadmap-ai",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true
    )
    GrowthRoadmapGenerator unavailableGrowthRoadmapGenerator() {
        return input -> {
            throw new GrowthRoadmapGenerationException(
                    "Growth roadmap AI generation is disabled.", false
            );
        };
    }
}
