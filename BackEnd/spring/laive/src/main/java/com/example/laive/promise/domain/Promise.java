package com.example.laive.promise.domain;

import com.example.laive.common.api.PatchValue;
import com.example.laive.promise.exception.PromiseException;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 약속의 불변조건과 상태 전이 규칙을 보관하는 도메인 객체다 (API 10.8).
 *
 * <p>DB 복원·확정·수정 어느 경로로 만들어져도 같은 생성자를 통과하므로 규칙이 한 곳에 모인다.
 * 상태 CHECK 제약이 DB에서 같은 규칙을 한 번 더 보장한다.
 *
 * <p>dueDate는 null일 수 있다. 기한 미정 약속과 조건부 약속("구독자 1000명 되면")이
 * 여기 해당한다 (API 1.5).
 *
 * <p>detectedAt과 calendarEventId는 promises 컬럼이 아니라 조회 시 계산·조인해 오는
 * 파생값이다. UPDATE 대상이 아니므로 전이 메서드는 받은 값을 그대로 옮긴다.
 */
public record Promise(
        long promiseId,
        long streamerId,
        long sessionId,
        long transcriptId,
        String content,
        PromiseStatus status,
        LocalDate dueDate,
        Instant detectedAt,
        Long calendarEventId
) {
    private static final int CONTENT_MAX_LENGTH = 500;

    public Promise {
        if (status == null) {
            throw PromiseException.invalidStatus();
        }
        content = normalizeContent(content);
    }

    /**
     * 감지된 약속을 확정한다 (API 10.8.2).
     *
     * <p>스트리머가 고친 내용·기한을 반영하고 PENDING으로 옮긴다. 확정 화면에서 기한을
     * 지우고 저장하면 dueDate가 null이 되어 캘린더에서 빠진다 — 의도된 동작이다.
     */
    public Promise confirm(String newContent, LocalDate newDueDate) {
        requireDetected();
        return new Promise(
                promiseId,
                streamerId,
                sessionId,
                transcriptId,
                newContent == null ? content : newContent,
                PromiseStatus.PENDING,
                newDueDate,
                detectedAt,
                calendarEventId
        );
    }

    /** 감지된 약속을 무시 처리한다 (API 10.8.2). */
    public Promise dismiss() {
        requireDetected();
        return withStatus(PromiseStatus.DISMISSED);
    }

    /**
     * 확정 이후의 부분 수정을 병합한다 (API 10.8.3 확장).
     *
     * <p>명세는 status만 받게 돼 있었으나, 캘린더에서 약속을 끌어 날짜를 옮기려면
     * 확정 이후에도 dueDate를 바꿀 수 있어야 해서 content·dueDate를 함께 연다.
     * 셋 다 선택이며 보낸 것만 반영한다.
     *
     * <p>PatchValue를 쓰는 이유는 "필드 미전송"과 "명시적 null"을 구분하기 위해서다.
     * 그 구분이 없으면 한 번 넣은 기한을 다시 비울 방법이 없다.
     */
    public Promise applyPatch(UpdatePromiseCommand command) {
        requirePatchable();
        if (command.isEmpty()) {
            throw PromiseException.emptyPatch();
        }

        PromiseStatus nextStatus = PatchValue.resolve(command.status(), status);
        if (nextStatus == null || !nextStatus.isPatchTarget()) {
            throw PromiseException.invalidStatus();
        }

        return new Promise(
                promiseId,
                streamerId,
                sessionId,
                transcriptId,
                PatchValue.resolve(command.content(), content),
                nextStatus,
                PatchValue.resolve(command.dueDate(), dueDate),
                detectedAt,
                calendarEventId
        );
    }

    /**
     * 약속을 캘린더 일정으로 전환한 상태로 옮긴다.
     *
     * <p>dueDate는 일부러 건드리지 않는다. 일정 취소로 PENDING으로 돌아왔을 때 기한이
     * 비어 있어야 "기한 미정" 목록에 다시 나타나 사용자가 새 날짜를 정할 수 있다.
     * 여기서 일정 시각을 dueDate에 복사해 두면 취소 후에도 캘린더 칩으로만 남는다.
     */
    public Promise schedule() {
        if (!status.isSchedulable()) {
            throw PromiseException.notSchedulable();
        }
        return withStatus(PromiseStatus.SCHEDULED);
    }

    /**
     * 연결된 일정이 취소돼 다시 기한 미정으로 돌아온다.
     *
     * <p>이 전이가 없으면 취소된 일정의 약속이 SCHEDULED로 굳어 캘린더에도 목록에도
     * 안 보이는 미아가 된다.
     */
    public Promise revertToPending() {
        if (!isScheduled()) {
            throw PromiseException.notScheduled();
        }
        return withStatus(PromiseStatus.PENDING);
    }

    public boolean isScheduled() {
        return status == PromiseStatus.SCHEDULED;
    }

    /** 캘린더에 표시할 약속인지. 기한이 있고 진행 중이어야 한다. */
    public boolean showsOnCalendar() {
        return dueDate != null && status.showsOnCalendar();
    }

    private Promise withStatus(PromiseStatus next) {
        return new Promise(
                promiseId, streamerId, sessionId, transcriptId,
                content, next, dueDate, detectedAt, calendarEventId
        );
    }

    private void requireDetected() {
        if (!status.isDetected()) {
            throw PromiseException.notDetected();
        }
    }

    /** 확정 전(DETECTED)과 이미 버린(DISMISSED) 약속은 PATCH를 막는다. */
    private void requirePatchable() {
        if (!status.isPatchSource()) {
            throw PromiseException.notConfirmed();
        }
    }

    private static String normalizeContent(String value) {
        if (value == null) {
            throw PromiseException.invalidContent();
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > CONTENT_MAX_LENGTH) {
            throw PromiseException.invalidContent();
        }
        return normalized;
    }
}
