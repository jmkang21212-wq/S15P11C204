package com.example.laive.promise.domain;

import java.time.LocalDate;

/**
 * 캘린더 화면에 얹을 약속 한 건이다.
 *
 * <p>기한만 잡힌 약속은 calendar_events에 복사하지 않는다. 복사하면 같은 약속이 두 행이 되어
 * 상태가 갈라지기 때문이다(약속은 COMPLETED인데 일정은 SCHEDULED로 남는 식).
 * promises를 단일 원천으로 두고 캘린더는 읽기 뷰로만 다룬다.
 *
 * <p>사용자가 명시적으로 일정으로 전환한 약속은 이야기가 다르다. 그때는 실제 일정 행이 생기고
 * 약속은 SCHEDULED가 되어 이 목록에서 빠진다 — 일정이 대신 보인다.
 *
 * <p>calendarEventId는 그 연결이 아직 남아 있는 경우를 위한 역참조다. 전환된 약속을 다시
 * PENDING으로 PATCH하면 일정은 살아 있는 채로 목록에 돌아오므로 null이 아닐 수 있다.
 */
public record PromiseCalendarItem(
        long promiseId,
        String content,
        LocalDate dueDate,
        PromiseStatus status,
        Long calendarEventId
) {
}
