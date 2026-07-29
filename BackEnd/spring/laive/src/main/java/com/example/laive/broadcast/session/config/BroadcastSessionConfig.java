package com.example.laive.broadcast.session.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.laive.broadcast.session.integration.AiSessionGateway;
import com.example.laive.broadcast.session.integration.ChatCollectorGateway;
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

    @Bean
    @ConditionalOnMissingBean
    ChatCollectorGateway chatCollectorGateway() {
        // 실제 치지직 채팅 수집기가 연결되기 전에도 세션 생명주기를 검증할 수 있는 안전한 기본 Port다.
        return new ChatCollectorGateway() {
        };
    }

    @Bean
    @ConditionalOnMissingBean
    AiSessionGateway aiSessionGateway() {
        // 실제 STT/AI 스케줄러 Bean이 제공되면 이 기본 Port는 자동으로 교체된다.
        return new AiSessionGateway() {
        };
    }

}
