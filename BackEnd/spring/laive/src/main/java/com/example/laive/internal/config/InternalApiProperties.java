package com.example.laive.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 내부 수집·AI API(명세 13)의 인증과 유량 제한 설정이다.
 *
 * @param serviceToken   수집기·AI 서버가 보내는 Bearer 토큰. 비어 있으면 /internal/**를 전부 거부한다.
 * @param requestIdTtl   X-Request-Id 멱등 기록의 Redis 보관 기간
 * @param maxBatchItems  배치 요청 하나가 담을 수 있는 최대 항목 수
 */
@ConfigurationProperties(prefix = "laive.internal")
public record InternalApiProperties(
        String serviceToken,
        Duration requestIdTtl,
        int maxBatchItems
) {

    public boolean hasServiceToken() {
        return serviceToken != null && !serviceToken.isBlank();
    }
}
