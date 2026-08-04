package com.example.laive.streamer.risk.domain;

/**
 * 금칙어가 걸렸을 때 알릴 등급이다. DB CHECK 제약(ck_srt_severity)과 이름을 동일하게 유지한다.
 *
 * <p>금칙어는 기본 사전에 없던 표기라 물려받을 등급이 없는데, 경고에는 severity가
 * 필요하다(AI/FRONTEND_API.md §4). 그래서 등록 시 고르게 하고 생략하면 {@link #MEDIUM}으로 본다.
 *
 * <p>허용어에는 쓰지 않는다 — 감지를 없애는 쪽이라 등급이라는 개념이 없다.
 */
public enum RiskTermSeverity {
    LOW,
    MEDIUM,
    HIGH;

    /** 등록 시 생략됐을 때의 값. DB에는 NULL로 들어간다. */
    public static final RiskTermSeverity DEFAULT = MEDIUM;

    public static RiskTermSeverity orDefault(RiskTermSeverity severity) {
        return severity == null ? DEFAULT : severity;
    }
}
