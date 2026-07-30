package com.example.laive.promise.dto.request;

import com.example.laive.common.api.PatchValue;
import com.example.laive.promise.domain.PromiseStatus;
import com.example.laive.promise.domain.UpdatePromiseCommand;

import java.time.LocalDate;

/**
 * 확정 약속 부분 수정 API 입력이다 (API 10.8.3 확장).
 *
 * <p>명세 원안은 status만 받았으나, 캘린더에서 약속을 끌어 날짜를 옮기려면 확정 이후에도
 * dueDate를 바꿀 수 있어야 해서 content·dueDate를 함께 연다. 드래그 드롭은
 * {@code {"dueDate": "2026-08-20"}} 하나만 보내면 된다.
 *
 * <p>값 범위 검증은 도메인 생성자에 맡긴다. PatchValue로 감싸면 jakarta 애노테이션이
 * 안쪽 값에 닿지 않기 때문이다.
 */
public record UpdatePromiseRequest(
        PatchValue<PromiseStatus> status,
        PatchValue<String> content,
        PatchValue<LocalDate> dueDate
) {
    public UpdatePromiseCommand toCommand() {
        return new UpdatePromiseCommand(status, content, dueDate);
    }
}
