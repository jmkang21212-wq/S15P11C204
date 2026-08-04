package com.example.laive.broadcast.collect.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 치지직 채팅 수집기 제어 서비스에 대한 Spring 쪽 설정이다.
 *
 * @param baseUrl          수집기 제어 서비스 주소. 비어 있으면 no-op Gateway를 쓴다
 * @param controlToken     Spring이 수집기를 호출할 때 보내는 Bearer 토큰
 * @param requestTimeout   제어 호출 Timeout
 * @param heartbeatTimeout 이 시간 동안 상태 보고가 없으면 STOPPED로 간주한다
 */
@ConfigurationProperties(prefix = "laive.collector")
public record CollectorProperties(
        String baseUrl,
        String controlToken,
        Duration requestTimeout,
        Duration heartbeatTimeout
) {

    public boolean isEnabled() {
        return baseUrl != null && !baseUrl.isBlank();
    }
}
