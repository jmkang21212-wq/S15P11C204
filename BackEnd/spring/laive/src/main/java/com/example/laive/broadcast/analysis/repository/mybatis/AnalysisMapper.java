package com.example.laive.broadcast.analysis.repository.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL 전용 SQL은 resources/mapper/broadcast/AnalysisMapper.xml에 둔다.
 */
@Mapper
public interface AnalysisMapper {

    Optional<AnalysisSessionRef> findSessionRef(@Param("sessionId") long sessionId);

    /** (session_id, start_ms, end_ms) Unique로 Upsert하고 id와 신규 여부를 돌려준다. */
    TranscriptRow upsertTranscript(
            @Param("sessionId") long sessionId,
            @Param("item") TranscriptUpsert item
    );

    List<TranscriptRow> upsertTranscripts(
            @Param("sessionId") long sessionId,
            @Param("items") List<TranscriptUpsert> items
    );

    Long insertRiskEvent(
            @Param("sessionId") long sessionId,
            @Param("transcriptId") long transcriptId,
            @Param("riskLevel") String riskLevel,
            @Param("riskCategory") String riskCategory,
            @Param("advice") String advice
    );

    Long insertPromise(
            @Param("streamerId") long streamerId,
            @Param("sessionId") long sessionId,
            @Param("transcriptId") long transcriptId,
            @Param("content") String content,
            @Param("dueDate") LocalDate dueDate
    );

    Long insertBriefing(
            @Param("sessionId") long sessionId,
            @Param("briefingType") String briefingType,
            @Param("message") String message
    );
}
