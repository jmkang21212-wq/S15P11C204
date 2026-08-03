package com.example.laive.platform.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 방송 플랫폼 연동 설정을 활성화하고 전용 HTTP 클라이언트를 제공한다.
 * Clock은 인증 도메인 설정의 빈을 재사용한다.
 */
@Configuration
@EnableConfigurationProperties(PlatformProperties.class)
public class PlatformConfig {

    // 외부 플랫폼 장애가 요청 스레드를 붙잡지 않도록 상한을 둔다.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    /**
     * 치지직 호출 전용 RestClient. 공유 Builder 빈을 변형하지 않도록 복제본에 타임아웃을 적용한다.
     */
    @Bean
    RestClient chzzkRestClient(RestClient.Builder restClientBuilder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return restClientBuilder.clone().requestFactory(requestFactory).build();
    }

    /** YouTube OAuth와 Data API가 공유하는 전용 HTTP Client다. */
    @Bean
    RestClient youtubeRestClient(RestClient.Builder restClientBuilder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return restClientBuilder.clone().requestFactory(requestFactory).build();
    }
}
