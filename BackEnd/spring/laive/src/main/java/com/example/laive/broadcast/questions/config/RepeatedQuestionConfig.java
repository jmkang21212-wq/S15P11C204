package com.example.laive.broadcast.questions.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 반복 질문 슬라이스 설정을 켠다. chat-ai HTTP 연결 자체는 {@code RealtimeConfig}의
 * {@code ChatAiGateway} 빈을 그대로 쓴다 — base-url 미설정 시 no-op 폴백도 함께 따라온다.
 */
@Configuration
@EnableConfigurationProperties(RepeatedQuestionProperties.class)
public class RepeatedQuestionConfig {
}
