package com.example.laive.promise.dto.request;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 감지 약속 확정 API 입력이다 (API 10.8.2).
 *
 * <p>둘 다 선택이다. 내용을 안 보내면 AI가 뽑은 문구를 그대로 쓰고, 기한을 안 보내면
 * 기한 미정으로 확정된다. 기한을 넣으면 그 순간부터 캘린더에 표시된다.
 */
public record ConfirmPromiseRequest(
        @Size(min = 1, max = 500, message = "1자 이상 500자 이하여야 합니다.")
        String content,

        LocalDate dueDate
) {
}
