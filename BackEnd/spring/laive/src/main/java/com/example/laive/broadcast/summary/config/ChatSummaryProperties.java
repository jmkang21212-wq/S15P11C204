package com.example.laive.broadcast.summary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * LLM 여론 요약 AUTO 스케줄러 파라미터다. 계약 원본은 {@code AI/CHAT_AI_API.md} §2.
 *
 * @param autoInterval AUTO 요약 주기. 계약의 "3분 주기 스케줄러" — chat-ai 쪽
 *                     쿨다운(60초)·최소 채팅(30건) 가드가 있어 줄여도 LLM 비용이
 *                     새지 않지만, 브리핑 피드의 갱신 리듬은 이 값이 결정한다
 */
@ConfigurationProperties(prefix = "laive.chat-summary")
public record ChatSummaryProperties(
        Duration autoInterval
) {
}
