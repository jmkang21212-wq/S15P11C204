package com.example.laive.broadcast.summary.repository.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 여론 요약 브리핑({@code live_briefings}의 CHAT_SUMMARY 행) 저장소다.
 * PostgreSQL 전용 SQL은 resources/mapper/broadcast/ChatSummaryMapper.xml에 둔다.
 */
@Mapper
public interface ChatSummaryMapper {

    /** AUTO 요약 대상 세션. 반복 질문 폴러와 같은 조건(PREPARING·LIVE)이다. */
    List<Long> findAutoSummarySessionIds();

    /**
     * 마지막으로 저장한 요약 텍스트다. AUTO와 MANUAL이 같은 요약을 두 번
     * 저장하지 않게 insert 전에 비교하는 dedupe 근거다.
     */
    Optional<String> findLatestSummaryMessage(@Param("sessionId") long sessionId);

    /** CHAT_SUMMARY 브리핑을 한 건 저장하고 id를 돌려준다. */
    long insertChatSummaryBriefing(@Param("sessionId") long sessionId, @Param("message") String message);
}
