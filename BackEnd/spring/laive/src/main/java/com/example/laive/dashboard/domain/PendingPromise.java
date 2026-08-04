package com.example.laive.dashboard.domain;

import java.time.Instant;
import java.time.LocalDate;

public record PendingPromise(
        long promiseId,
        long sessionId,
        String content,
        PromiseStatus status,
        LocalDate dueDate,
        Instant detectedAt
) {
}
