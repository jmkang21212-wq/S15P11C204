package com.example.laive.broadcast.realtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 채팅 감정 분류 AI(chat-ai) 연결 설정이다.
 *
 * @param baseUrl       chat-ai 서버 주소(기본 포트 8001). 비어 있으면 no-op Gateway를 쓴다
 * @param token         Bearer 사전공유 토큰. chat-ai 쪽 CHATAI_TOKEN과 같아야 한다
 * @param ingestTimeout ingest 호출 Timeout. 계약 권장값은 2초 — 이 시간은 수집기
 *                      배치 응답 지연에 그대로 더해지므로 늘릴 때는 수집기 쪽
 *                      HTTP Timeout도 함께 봐야 한다
 */
@ConfigurationProperties(prefix = "laive.chat-ai")
public record ChatAiProperties(
        String baseUrl,
        String token,
        Duration ingestTimeout
) {

    public boolean isEnabled() {
        return baseUrl != null && !baseUrl.isBlank();
    }
}
