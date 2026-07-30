package com.example.laive.promise.repository;

import com.example.laive.promise.domain.Promise;
import com.example.laive.promise.domain.PromiseCalendarItem;
import com.example.laive.promise.domain.PromiseStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 약속 관리 애플리케이션 계층이 사용하는 영속성 Port다.
 */
public interface PromiseRepository {

    /** status가 null이면 전체 상태를 조회한다 (API 10.8.1). */
    List<Promise> findPage(long streamerId, PromiseStatus status, int limit, int offset);

    long countAll(long streamerId, PromiseStatus status);

    Optional<Promise> findForUpdate(long streamerId, long promiseId);

    int update(Promise promise);

    /** 캘린더 범위에 걸리는 진행 중 약속을 찾는다. */
    List<PromiseCalendarItem> findCalendarRange(long streamerId, LocalDate from, LocalDate to);
}
