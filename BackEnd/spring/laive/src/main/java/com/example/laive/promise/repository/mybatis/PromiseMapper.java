package com.example.laive.promise.repository.mybatis;

import com.example.laive.promise.domain.Promise;
import com.example.laive.promise.domain.PromiseCalendarItem;
import com.example.laive.promise.domain.PromiseStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Mapper
public interface PromiseMapper {

    List<Promise> findPage(
            @Param("streamerId") long streamerId,
            @Param("status") PromiseStatus status,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long countAll(
            @Param("streamerId") long streamerId,
            @Param("status") PromiseStatus status
    );

    Optional<Promise> findForUpdate(
            @Param("streamerId") long streamerId,
            @Param("promiseId") long promiseId
    );

    int update(@Param("promise") Promise promise);

    List<PromiseCalendarItem> findCalendarRange(
            @Param("streamerId") long streamerId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );
}
