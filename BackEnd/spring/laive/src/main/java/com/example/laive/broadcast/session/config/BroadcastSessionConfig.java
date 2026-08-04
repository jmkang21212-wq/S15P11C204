package com.example.laive.broadcast.session.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.laive.broadcast.session.integration.AiSessionGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 방송 세션 비동기 Worker와 외부 시스템 Port의 기본 구현을 구성한다.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(BroadcastSessionProperties.class)
public class BroadcastSessionConfig {

    @Bean
    @ConditionalOnMissingBean
    ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    // ChatCollectorGateway는 CollectorConfig가 등록한다. base-url 설정 여부에 따라
    // 실제 제어 Adapter와 no-op 중 하나를 고른다.

    @Bean
    @ConditionalOnMissingBean
    AiSessionGateway aiSessionGateway() {
        // 실제 STT/AI 스케줄러 Bean이 제공되면 이 기본 Port는 자동으로 교체된다.
        return new AiSessionGateway() {
        };
    }

}
