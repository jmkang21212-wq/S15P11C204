package com.example.laive.promise.service;

import com.example.laive.common.api.PageResponse;
import com.example.laive.promise.domain.Promise;
import com.example.laive.promise.domain.PromiseCalendarItem;
import com.example.laive.promise.domain.PromiseStatus;
import com.example.laive.promise.repository.PromiseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 소유권 조건이 포함된 약속 조회를 담당한다 (API 10.8.1).
 */
@Service
@Transactional(readOnly = true)
public class PromiseQueryService {

    /**
     * 캘린더 범위를 날짜로 자를 기준 시간대다.
     *
     * <p>due_date가 DATE라 UTC로 자르면 경계가 하루 어긋난다. 8월 1일 캘린더에 7월 31일
     * 약속이 뜨거나 그 반대가 된다. DB 컨테이너도 TZ=Asia/Seoul로 맞춰져 있다.
     */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final PromiseRepository repository;

    public PromiseQueryService(PromiseRepository repository) {
        this.repository = repository;
    }

    /** status가 null이면 전체 상태를 조회한다. */
    public PageResponse<Promise> getPromises(long streamerId, PromiseStatus status, int page, int size) {
        long total = repository.countAll(streamerId, status);
        if (total == 0) {
            return PageResponse.of(List.of(), page, size, 0);
        }
        List<Promise> items = repository.findPage(streamerId, status, size, page * size);
        return PageResponse.of(items, page, size, total);
    }

    /**
     * 캘린더 기간에 걸치는 약속을 찾는다.
     *
     * <p>약속은 시각이 없는 날짜라, 캘린더 조회 구간(Instant)을 서비스 시간대의 날짜로
     * 바꿔 비교한다. to는 그 시각이 속한 날짜까지 포함한다 — 화면에 보이는 마지막 날의
     * 약속이 빠지지 않게 하기 위해서다.
     */
    public List<PromiseCalendarItem> getCalendarPromises(long streamerId, Instant from, Instant to) {
        LocalDate fromDate = LocalDate.ofInstant(from, SERVICE_ZONE);
        LocalDate toDate = LocalDate.ofInstant(to, SERVICE_ZONE);
        return repository.findCalendarRange(streamerId, fromDate, toDate);
    }
}
