package com.example.laive.broadcast.session.domain;

import com.example.laive.broadcast.session.exception.BroadcastSessionException;

import java.time.Instant;
import java.util.Objects;

/**
 * 방송 세션 상태 전이 규칙을 보관하는 순수 도메인 객체다.
 *
 * MyBatis에는 변경 감지가 없으므로 메서드로 상태를 검증한 뒤 Repository의 조건부 SQL을
 * 명시적으로 호출해야 한다.
 */
public final class BroadcastSession {

    private final Long id;
    private final long streamerId;
    private final long platformAccountId;
    private final Long calendarEventId;
    private final String title;
    private final String category;
    private final String language;
    private final String thumbnailUrl;
    private final String vodUrl;
    private BroadcastStatus status;
    private final Instant startedAt;
    private Instant endedAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private BroadcastSession(
            Long id,
            long streamerId,
            long platformAccountId,
            Long calendarEventId,
            String title,
            String category,
            String language,
            String thumbnailUrl,
            String vodUrl,
            BroadcastStatus status,
            Instant startedAt,
            Instant endedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.streamerId = streamerId;
        this.platformAccountId = platformAccountId;
        this.calendarEventId = calendarEventId;
        this.title = normalizeTitle(title);
        this.category = normalizeOptional(category);
        this.language = normalizeLanguage(language);
        this.thumbnailUrl = thumbnailUrl;
        this.vodUrl = vodUrl;
        this.status = Objects.requireNonNull(status, "status");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.endedAt = endedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** 사용자의 시작 요청으로 PREPARING 세션을 만든다. */
    public static BroadcastSession prepare(
            long streamerId,
            long platformAccountId,
            Long calendarEventId,
            String title,
            String category,
            String language,
            Instant startedAt
    ) {
        return new BroadcastSession(
                null,
                streamerId,
                platformAccountId,
                calendarEventId,
                title,
                category,
                language,
                null,
                null,
                BroadcastStatus.PREPARING,
                startedAt,
                null,
                null,
                null
        );
    }

    /** DB에서 조회한 값을 상태 검증이 가능한 도메인 객체로 복원한다. */
    public static BroadcastSession reconstitute(
            Long id,
            long streamerId,
            long platformAccountId,
            Long calendarEventId,
            String title,
            String category,
            String language,
            String thumbnailUrl,
            String vodUrl,
            BroadcastStatus status,
            Instant startedAt,
            Instant endedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new BroadcastSession(
                id,
                streamerId,
                platformAccountId,
                calendarEventId,
                title,
                category,
                language,
                thumbnailUrl,
                vodUrl,
                status,
                startedAt,
                endedAt,
                createdAt,
                updatedAt
        );
    }

    /** 필수 수집기 초기화가 완료된 세션만 LIVE로 바꾼다. */
    public void activate(Instant changedAt) {
        if (status == BroadcastStatus.LIVE) {
            return;
        }
        if (status != BroadcastStatus.PREPARING) {
            throw BroadcastSessionException.stateConflict();
        }
        status = BroadcastStatus.LIVE;
        updatedAt = Objects.requireNonNull(changedAt, "changedAt");
    }

    /** PREPARING 또는 LIVE 세션을 정상 종료한다. */
    public void end(Instant finishedAt) {
        if (status == BroadcastStatus.ENDED) {
            throw BroadcastSessionException.alreadyEnded();
        }
        if (!status.isActive()) {
            throw BroadcastSessionException.stateConflict();
        }
        requireValidEndTime(finishedAt);
        status = BroadcastStatus.ENDED;
        endedAt = finishedAt;
        updatedAt = finishedAt;
    }

    /** 복구할 수 없는 초기화/수집 장애를 최종 실패 상태로 기록한다. */
    public void fail(Instant failedAt) {
        if (status == BroadcastStatus.FAILED) {
            return;
        }
        if (status == BroadcastStatus.ENDED) {
            throw BroadcastSessionException.stateConflict();
        }
        requireValidEndTime(failedAt);
        status = BroadcastStatus.FAILED;
        endedAt = failedAt;
        updatedAt = failedAt;
    }

    public boolean isActive() {
        return status.isActive();
    }

    public boolean isTerminal() {
        return status.isTerminal();
    }

    public boolean canEnd() {
        return status.isActive();
    }

    private void requireValidEndTime(Instant value) {
        Objects.requireNonNull(value, "endedAt");
        if (value.isBefore(startedAt)) {
            throw BroadcastSessionException.invalidPeriod();
        }
    }

    private static String normalizeTitle(String value) {
        String normalized = Objects.requireNonNull(value, "title").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String normalizeLanguage(String value) {
        return value == null || value.isBlank() ? "ko" : value.trim();
    }

    public Long id() {
        return id;
    }

    public long streamerId() {
        return streamerId;
    }

    public long platformAccountId() {
        return platformAccountId;
    }

    public Long calendarEventId() {
        return calendarEventId;
    }

    public String title() {
        return title;
    }

    public String category() {
        return category;
    }

    public String language() {
        return language;
    }

    public String thumbnailUrl() {
        return thumbnailUrl;
    }

    public String vodUrl() {
        return vodUrl;
    }

    public BroadcastStatus status() {
        return status;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant endedAt() {
        return endedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}

