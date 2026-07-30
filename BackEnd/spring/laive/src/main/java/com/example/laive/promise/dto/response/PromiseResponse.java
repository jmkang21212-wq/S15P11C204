package com.example.laive.promise.dto.response;

import com.example.laive.promise.domain.Promise;
import com.example.laive.promise.domain.PromiseStatus;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 약속 한 건의 응답이다 (API 10.8.1).
 *
 * <p>필드 구성은 프론트 목업(FrontEnd api/dashboard.ts의 pendingPromises)과 맞춘 것이므로
 * 이름을 바꾸지 말 것. 바꾸는 순간 프론트 연동 비용이 생긴다.
 */
public record PromiseResponse(
        long promiseId,
        long sessionId,
        long transcriptId,
        String content,
        PromiseStatus status,
        LocalDate dueDate,
        Instant detectedAt
) {
    public static PromiseResponse from(Promise promise) {
        return new PromiseResponse(
                promise.promiseId(),
                promise.sessionId(),
                promise.transcriptId(),
                promise.content(),
                promise.status(),
                promise.dueDate(),
                promise.detectedAt()
        );
    }
}
