package com.example.laive.broadcast.realtime.repository.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * PostgreSQL 전용 SQL은 resources/mapper/broadcast/TriggerMapper.xml에 둔다.
 */
@Mapper
public interface TriggerMapper {

    /**
     * 판정 대상으로 등록할 수 있는 세션의 시작 시각을 찾는다.
     * 이미 끝난 세션이면 비어 있다 — 종료 후 지연 배치가 판정기를 되살리지 않게 한다.
     */
    Optional<Instant> findActiveSessionStartedAt(@Param("sessionId") long sessionId);

    /**
     * 확정된 CHAT_SURGE 구간을 구간 내 통계와 함께 한 문장으로 저장한다.
     * 같은 스냅샷에서 전 컬럼을 집계하므로 8종 합 == analyzed_chat CHECK가
     * 구조적으로 성립한다.
     */
    int insertChatSurgeTrigger(
            @Param("sessionId") long sessionId,
            @Param("startMs") long startMs,
            @Param("endMs") long endMs,
            @Param("startedAt") Instant startedAt,
            @Param("endedAt") Instant endedAt,
            @Param("peakZ") BigDecimal peakZ,
            @Param("peakEngagement") BigDecimal peakEngagement
    );
}
