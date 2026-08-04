package com.example.laive.broadcast.collect.config;

import com.example.laive.broadcast.collect.integration.CollectorStatusStore;
import com.example.laive.broadcast.collect.integration.HttpChatCollectorGateway;
import com.example.laive.broadcast.collect.service.CollectionIngestService;
import com.example.laive.broadcast.session.integration.ChatCollectorGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 치지직 채팅 수집기 제어 Port를 구성한다.
 */
@Configuration
@EnableConfigurationProperties(CollectorProperties.class)
public class CollectorConfig {

    private static final Logger log = LoggerFactory.getLogger(CollectorConfig.class);

    /**
     * base-url이 설정된 환경에서만 실제 제어 Adapter를 쓴다.
     *
     * <p>미설정 시 no-op으로 두는 이유: 수집기 없이도 방송 세션 생명주기와 나머지
     * API를 로컬에서 돌리고 테스트할 수 있어야 한다. 조건부 Bean 등록 대신 여기서
     * 분기하는 것은 {@code @ConditionalOnMissingBean}이 사용자 {@code @Configuration}
     * 사이에서는 평가 순서에 의존해 조용히 어긋날 수 있기 때문이다.
     */
    @Bean
    ChatCollectorGateway chatCollectorGateway(
            RestClient.Builder restClientBuilder,
            CollectorProperties properties,
            CollectionIngestService ingestService,
            CollectorStatusStore statusStore
    ) {
        if (!properties.isEnabled()) {
            log.warn("laive.collector.base-url이 비어 있어 채팅 수집기를 연결하지 않습니다. "
                    + "방송 세션은 동작하지만 채팅과 시청자 지표가 수집되지 않습니다.");
            return new ChatCollectorGateway() {
            };
        }

        RestClient restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory(properties))
                .build();
        return new HttpChatCollectorGateway(restClient, properties, ingestService, statusStore);
    }

    /**
     * 제어 호출에 Timeout을 건다.
     *
     * <p>기본 Factory는 무한 대기라, 수집기가 응답하지 않으면 준비 이벤트를 처리하던
     * Outbox 워커 스레드가 그대로 묶인다. 워커 수는 유한하므로 한 세션의 지연이
     * 다른 방송의 준비까지 멈추게 한다.
     */
    private JdkClientHttpRequestFactory requestFactory(CollectorProperties properties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(properties.requestTimeout());
        return factory;
    }
}
