package com.example.laive.broadcast.realtime.domain;

/**
 * 여론 요약 호출 주체다(계약 §2). chat-ai 쪽 처리 규칙은 동일하고 로그·통계
 * 구분용으로만 쓰인다.
 */
public enum SummaryMode {
    /** 3분 주기 스케줄러. */
    AUTO,
    /** 프론트 수동 버튼. */
    MANUAL
}
