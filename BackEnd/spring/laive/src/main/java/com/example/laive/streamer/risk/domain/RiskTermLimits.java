package com.example.laive.streamer.risk.domain;

/**
 * 개인 사전 등록 제한값이다 (AI/RISK_POLICY_SPEC.md §6).
 *
 * <p>설정으로 빼지 않고 상수로 둔 이유: 이 값들은 운영 중 조절하는 튜닝 파라미터가 아니라
 * 매칭 엔진의 성질에서 나온 제약이다. 낮추면 기존 데이터가 상한을 넘고, 올리면 티켓 크기와
 * 오탐이 함께 늘어난다.
 */
public final class RiskTermLimits {

    /**
     * 종류당 등록 상한.
     *
     * <p>티켓 크기의 상한이기도 하다 — 200개 × 40자 × 2종류면 최악의 경우도 16KB 남짓이라
     * Redis 티켓에 그대로 실을 수 있다. 이 상한이 있어서 사전 조회용 내부 API를 따로
     * 만들지 않아도 된다.
     */
    public static final int MAX_PER_KIND = 200;

    /**
     * 정규화 후 표기 길이 상한.
     *
     * <p>사전 매칭은 부분 문자열 탐색이라 긴 문장은 어차피 통째로 일치하는 일이 없다.
     */
    public static final int MAX_TERM_LENGTH = 40;

    /**
     * 정규화 후 표기 길이 하한.
     *
     * <p>한 글자는 다른 단어에 묻힌다 (app/detect/terms.yaml 설계 원칙 3:
     * 스발←가스발전, 병시←질병시). 두 글자도 위험하지만 그건 막지 않고 경고만 한다.
     */
    public static final int MIN_TERM_LENGTH = 2;

    private RiskTermLimits() {
    }
}
