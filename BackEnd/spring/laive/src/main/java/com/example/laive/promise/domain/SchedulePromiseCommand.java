package com.example.laive.promise.domain;

import java.time.Instant;

/**
 * 약속을 캘린더 일정으로 전환하는 입력이다.
 *
 * <p>platformAccountId가 없는 것은 의도된 것이다. 전환은 항상 BROADCAST 일정을 만들고,
 * 어느 계정에 붙일지는 서버가 스트리머의 연결된 계정에서 고른다 — 사용자가 방송 예정을
 * 확정하는 화면에서 계정을 다시 고르게 할 이유가 없다.
 *
 * <p>title이 null이면 약속 content를 제목 길이(200자)로 잘라 쓴다.
 */
public record SchedulePromiseCommand(
        Instant startAt,
        Instant endAt,
        String title,
        String memo
) {
}
