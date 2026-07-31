package com.example.laive.broadcast.questions.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 반복 질문 폴링·재질문 감지 파라미터다. 계약 원본은 {@code AI/CHAT_AI_API.md} §3의
 * "파라미터 기본값" 표 — 유예·임계는 Spring 상수로 명시돼 있다.
 *
 * @param pollInterval chat-ai 폴링 주기. 계약 권장 10~30초
 * @param limit        폴링당 받아올 질문 수. 계약 최대 50 — dismissed 보존과 세션
 *                     리포트 마감을 위해 전량을 받는다(exclude 사용 금지)
 * @param dismissGrace 답변 완료 후 재질문 판정 기준(baseline)을 잡지 않는 유예.
 *                     송출 지연 4~5초 + 시청자 인지 + 타이핑 중이던 사람들의 관성
 *                     유입을 흡수한다
 * @param reexpose     재노출 임계. 고정값이 아니라 시청자 수에 비례한다 — 아래 참조
 */
@ConfigurationProperties(prefix = "laive.repeated-questions")
public record RepeatedQuestionProperties(
        Duration pollInterval,
        int limit,
        Duration dismissGrace,
        Reexpose reexpose
) {

    /**
     * 재노출 임계 = clamp(⌈시청자 × ratio⌉, min, max). "답변 후 10명이 더 물었다"는
     * 시청자 300명 방송과 3만 명 방송에서 전혀 다른 사건이라 고정값을 쓸 수 없다 —
     * 소규모 채널에서는 임계에 영영 못 미쳐 재노출이 죽은 기능이 되고, 대형 채널에서는
     * 유예(60초)를 넘겨 도착하는 관성 유입만으로 임계를 넘겨 오탐이 난다.
     *
     * @param ratio    시청자 대비 비율. 기본 0.005 — 시청자 2,000명에서 기존 고정
     *                 임계 +10과 같아지도록 잡았다(중간 규모 채널 실측 기준의 연속성)
     * @param min      하한. 초소형 채널에서 한두 명의 재질문으로 튀지 않게 막는다
     * @param max      상한. 초대형 채널에서 임계가 세 자릿수로 치솟아 재노출이
     *                 사실상 꺼지는 것을 막는다
     * @param fallback 시청자 수를 모를 때 쓰는 값. 수신 5분 초과·재시작 직후·종료
     *                 마감 폴링(registry가 먼저 정리된다)이 이 경로다
     */
    public record Reexpose(double ratio, int min, int max, int fallback) {

        /** 판정 시점 시청자 수로 임계를 계산한다. null·0 이하면 fallback이다. */
        public int thresholdFor(Integer viewers) {
            if (viewers == null || viewers <= 0) {
                return fallback;
            }
            long scaled = (long) Math.ceil(viewers * ratio);
            return (int) Math.max(min, Math.min(max, scaled));
        }
    }
}
