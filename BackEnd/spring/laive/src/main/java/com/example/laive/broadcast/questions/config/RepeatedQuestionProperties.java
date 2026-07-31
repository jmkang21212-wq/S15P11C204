package com.example.laive.broadcast.questions.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 반복 질문 폴링·재질문 감지 파라미터다. 계약 원본은 {@code AI/CHAT_AI_API.md} §3의
 * "파라미터 기본값" 표 — 유예·임계는 Spring 상수로 명시돼 있다.
 *
 * @param pollInterval      chat-ai 폴링 주기. 계약 권장 10~30초
 * @param limit             폴링당 받아올 질문 수. 계약 최대 50 — dismissed 보존과 세션
 *                          리포트 마감을 위해 전량을 받는다(exclude 사용 금지)
 * @param dismissGrace      답변 완료 후 재질문 판정 기준(baseline)을 잡지 않는 유예.
 *                          송출 지연 4~5초 + 시청자 인지 + 타이핑 중이던 사람들의 관성
 *                          유입을 흡수한다
 * @param reexposeThreshold 답변 후 askers가 이만큼 더 늘면 다시 노출한다. 중간 규모
 *                          채널 실측 기준 — 시청자 수백 명대 채널에서는 도달하기
 *                          어려우니 규모별 조정 여지가 있다
 */
@ConfigurationProperties(prefix = "laive.repeated-questions")
public record RepeatedQuestionProperties(
        Duration pollInterval,
        int limit,
        Duration dismissGrace,
        int reexposeThreshold
) {
}
