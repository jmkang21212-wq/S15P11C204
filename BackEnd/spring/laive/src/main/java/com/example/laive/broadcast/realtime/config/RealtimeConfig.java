package com.example.laive.broadcast.realtime.config;

import com.example.laive.broadcast.realtime.integration.ChatAiGateway;
import com.example.laive.broadcast.realtime.integration.HttpChatAiGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 채팅 감정 분류 AI Port를 구성한다.
 *
 * <p>base-url 미설정 시 no-op으로 두는 분기 방식과 그 이유는 {@code CollectorConfig}와
 * 같다 — AI 없이도 수집·저장과 나머지 API가 로컬에서 돌아야 한다.
 */
@Configuration
@EnableConfigurationProperties({ChatAiProperties.class, HighlightProperties.class})
public class RealtimeConfig {

    private static final Logger log = LoggerFactory.getLogger(RealtimeConfig.class);

    @Bean
    ChatAiGateway chatAiGateway(RestClient.Builder restClientBuilder, ChatAiProperties properties) {
        if (!properties.isEnabled()) {
            log.warn("laive.chat-ai.base-url이 비어 있어 감정 분류 AI를 연결하지 않습니다. "
                    + "채팅은 sentiment_label 없이 저장되고 실시간 여론 지표가 갱신되지 않습니다.");
            return new ChatAiGateway() {
            };
        }

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        // 계약 권장 2초. 이 대기는 수집기 배치 응답에 그대로 더해지므로 짧게 유지한다.
        requestFactory.setReadTimeout(properties.ingestTimeout());

        RestClient restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        return new HttpChatAiGateway(restClient, properties);
    }
}
