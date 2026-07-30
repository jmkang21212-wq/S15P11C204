package com.example.laive.promise.domain;

import com.example.laive.common.api.PatchValue;

import java.time.LocalDate;

/**
 * 확정된 약속의 부분 수정 요청이다 (API 10.8.3 확장).
 *
 * <p>셋 다 선택이며 보낸 것만 반영한다. dueDate만 보내는 요청이 캘린더 드래그이고,
 * status만 보내는 요청이 명세 원안의 상태 변경이다.
 */
public record UpdatePromiseCommand(
        PatchValue<PromiseStatus> status,
        PatchValue<String> content,
        PatchValue<LocalDate> dueDate
) {
    public boolean isEmpty() {
        return status == null && content == null && dueDate == null;
    }
}
