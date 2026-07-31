package com.example.laive.growth.config;

import com.example.laive.growth.ai.GrowthActionGenerator;
import com.example.laive.growth.exception.GrowthActionGenerationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GrowthAiProperties.class)
public class GrowthAiConfiguration {
    @Bean
    @ConditionalOnMissingBean(GrowthActionGenerator.class)
    GrowthActionGenerator unavailableGrowthActionGenerator() {
        return input -> {
            throw new GrowthActionGenerationException("Growth action AI is unavailable.");
        };
    }
}
