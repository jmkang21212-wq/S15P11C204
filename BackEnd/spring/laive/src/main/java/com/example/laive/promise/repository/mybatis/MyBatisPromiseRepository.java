package com.example.laive.promise.repository.mybatis;

import com.example.laive.promise.domain.Promise;
import com.example.laive.promise.domain.PromiseCalendarItem;
import com.example.laive.promise.domain.PromiseStatus;
import com.example.laive.promise.repository.PromiseRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * MyBatis Mapper 타입이 Service 계층으로 노출되지 않게 하는 Repository Adapter다.
 */
@Repository
public class MyBatisPromiseRepository implements PromiseRepository {

    private final PromiseMapper mapper;

    public MyBatisPromiseRepository(PromiseMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Promise> findPage(long streamerId, PromiseStatus status, int limit, int offset) {
        return mapper.findPage(streamerId, status, limit, offset);
    }

    @Override
    public long countAll(long streamerId, PromiseStatus status) {
        return mapper.countAll(streamerId, status);
    }

    @Override
    public Optional<Promise> findForUpdate(long streamerId, long promiseId) {
        return mapper.findForUpdate(streamerId, promiseId);
    }

    @Override
    public int update(Promise promise) {
        return mapper.update(promise);
    }

    @Override
    public List<PromiseCalendarItem> findCalendarRange(long streamerId, LocalDate from, LocalDate to) {
        return mapper.findCalendarRange(streamerId, from, to);
    }
}
