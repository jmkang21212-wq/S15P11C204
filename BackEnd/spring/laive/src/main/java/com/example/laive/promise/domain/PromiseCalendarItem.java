package com.example.laive.promise.domain;

import java.time.LocalDate;

/**
 * 캘린더 화면에 얹을 약속 한 건이다.
 *
 * <p>약속은 calendar_events에 복사하지 않는다. 복사하면 같은 약속이 두 행이 되어
 * 상태가 갈라지기 때문이다(약속은 COMPLETED인데 일정은 SCHEDULED로 남는 식).
 * promises를 단일 원천으로 두고 캘린더는 읽기 뷰로만 다룬다.
 */
public record PromiseCalendarItem(
        long promiseId,
        String content,
        LocalDate dueDate,
        PromiseStatus status
) {
}
