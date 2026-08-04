package com.example.laive.media.dto.request;

import jakarta.validation.constraints.Size;

/**
 * 클립 생성 실패 보고다.
 *
 * @param reason      진단용 원인. {@code clip_failed_reason}(VARCHAR 200)에 잘려 들어간다
 * @param permanent   재시도해도 결과가 같은 실패인지. 녹화본이 사라진 경우가 대표적이며
 *                    true면 시도 횟수를 쓰지 않고 SKIPPED로 확정한다
 */
public record ClipFailureRequest(
        @Size(max = 200) String reason,
        boolean permanent
) {
}
