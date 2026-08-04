package com.example.laive.portfolio.domain;

import com.example.laive.common.api.PatchValue;
import com.example.laive.portfolio.exception.PortfolioException;

import java.time.Instant;

/**
 * portfolios 한 행의 도메인 객체다 (API 12).
 *
 * <p>실적 5개 컬럼(avg_viewers 등)은 여기 없다 — 스트리머가 편집하는 값이 아니라
 * 백엔드가 집계하는 측정값이라 읽기 모델({@link PortfolioPerformance})로 분리했다.
 *
 * <p>DB 복원·생성·수정 어느 경로로 만들어져도 같은 생성자를 통과하므로 값 규칙이
 * 한 곳에 모인다. introduction은 프론트 타입이 non-null string이라 null을 빈
 * 문자열로 눕힌다.
 */
public record Portfolio(
        long portfolioId,
        long streamerId,
        String publicSlug,
        String title,
        String introduction,
        String contactEmail,
        String contactUrl,
        boolean isPublic,
        Instant publishedAt
) {
    private static final int TITLE_MAX_LENGTH = 100;
    private static final int INTRODUCTION_MAX_LENGTH = 500;
    private static final int CONTACT_EMAIL_MAX_LENGTH = 255;
    private static final int CONTACT_URL_MAX_LENGTH = 1024;
    private static final String SLUG_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SLUG_LENGTH = 10;

    public Portfolio {
        title = normalizeTitle(title);
        introduction = normalizeIntroduction(introduction);
        contactEmail = normalizeContactEmail(contactEmail);
        contactUrl = normalizeContactUrl(contactUrl);
    }

    /**
     * 생성 요청으로 새 포트폴리오를 만든다 (자동 구성 없음 — 값은 전부 사용자 입력).
     *
     * <p>공개로 만들면 그 시각이 곧 publishedAt이다 (DB CHECK: 공개면 publishedAt 필수).
     */
    public static Portfolio create(
            long streamerId, String publicSlug, CreatePortfolioCommand command, Instant now) {
        return new Portfolio(
                0L,
                streamerId,
                publicSlug,
                command.title(),
                command.introduction(),
                command.contactEmail(),
                command.contactUrl(),
                command.isPublic(),
                command.isPublic() ? now : null
        );
    }

    /**
     * 공개 주소용 무작위 슬러그를 만든다.
     *
     * <p>{@code s-{streamerId}} 같은 결정적 슬러그는 내부 순번 id를 익명에게
     * 노출한다 — 명세가 공개 응답에서 의도적으로 뺀 값이라 무작위로 발급한다.
     * 소문자·숫자 10자라 DB 형식 CHECK {@code ^[a-z0-9][a-z0-9-]{2,49}$}를 항상
     * 통과하고, 36^10 조합이라 충돌은 사실상 없다(그래도 UNIQUE 위반 시 재시도).
     */
    public static String randomSlug(java.util.random.RandomGenerator random) {
        StringBuilder slug = new StringBuilder(SLUG_LENGTH);
        for (int i = 0; i < SLUG_LENGTH; i++) {
            slug.append(SLUG_ALPHABET.charAt(random.nextInt(SLUG_ALPHABET.length())));
        }
        return slug.toString();
    }

    /**
     * 부분 수정을 병합한다 (API 12.2).
     *
     * <p>publishedAt 전이 규칙: 비공개→공개로 바뀌는 순간 now로 기록한다(재공개도 갱신).
     * 공개 유지·비공개 전환 시에는 보존한다 — "마지막으로 공개한 시각"이라는 의미다.
     */
    public Portfolio applyPatch(UpdatePortfolioCommand command, Instant now) {
        if (command.isEmpty()) {
            throw PortfolioException.emptyPatch();
        }

        Boolean nextPublic = PatchValue.resolve(command.isPublic(), isPublic);
        if (nextPublic == null) {
            throw PortfolioException.invalidField("isPublic는 null일 수 없습니다.");
        }

        return new Portfolio(
                portfolioId,
                streamerId,
                publicSlug,
                PatchValue.resolve(command.title(), title),
                PatchValue.resolve(command.introduction(), introduction),
                PatchValue.resolve(command.contactEmail(), contactEmail),
                PatchValue.resolve(command.contactUrl(), contactUrl),
                nextPublic,
                nextPublic && !isPublic ? now : publishedAt
        );
    }

    /** 제목은 비울 수 없는 NOT NULL 필드다. 명시적 null·공백도 거절한다. */
    private static String normalizeTitle(String value) {
        if (value == null || value.isBlank()) {
            throw PortfolioException.invalidField("제목은 비울 수 없습니다.");
        }
        String normalized = value.trim();
        if (normalized.length() > TITLE_MAX_LENGTH) {
            throw PortfolioException.invalidField(
                    "제목은 " + TITLE_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return normalized;
    }

    private static String normalizeIntroduction(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() > INTRODUCTION_MAX_LENGTH) {
            throw PortfolioException.invalidField(
                    "소개는 " + INTRODUCTION_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return normalized;
    }

    private static String normalizeContactEmail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > CONTACT_EMAIL_MAX_LENGTH
                || !normalized.matches("[^@\\s]+@[^@\\s]+")) {
            throw PortfolioException.invalidField("연락 이메일 형식이 올바르지 않습니다.");
        }
        return normalized;
    }

    private static String normalizeContactUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > CONTACT_URL_MAX_LENGTH
                || !(normalized.startsWith("http://") || normalized.startsWith("https://"))) {
            throw PortfolioException.invalidField(
                    "연락 URL은 http(s)로 시작하는 " + CONTACT_URL_MAX_LENGTH + "자 이하 주소여야 합니다.");
        }
        return normalized;
    }
}
