package com.example.laive.broadcast.summary.service;

import com.example.laive.broadcast.realtime.domain.ChatAiSummary;
import com.example.laive.broadcast.summary.repository.mybatis.ChatSummaryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 여론 요약을 {@code live_briefings(CHAT_SUMMARY)}로 저장한다(명세 13.6 — Spring은
 * 성공 응답의 summary로 브리핑을 생성한다).
 *
 * <p>저장 전 마지막 요약 텍스트와 비교하는 이유: AUTO 스케줄러와 MANUAL 버튼이
 * 같은 요약을 각자 저장하거나, COOLDOWN 회수(GET)가 이미 저장된 요약을 다시
 * 물어올 수 있다. live_briefings에는 dedupe 키가 없어 텍스트 비교가 유일한
 * 방어선이다 — chat-ai 쿨다운(60초)당 최대 1건이라 비교 비용은 무시할 수준이다.
 */
@Service
public class ChatSummaryBriefingService {

    private static final Logger log = LoggerFactory.getLogger(ChatSummaryBriefingService.class);

    private final ChatSummaryMapper mapper;

    public ChatSummaryBriefingService(ChatSummaryMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 요약을 저장한다. 마지막 저장분과 같은 텍스트면 건너뛴다.
     *
     * @return 실제로 저장했으면 true
     */
    @Transactional
    public boolean append(long sessionId, ChatAiSummary summary) {
        String message = summary.summary();
        if (message == null || message.isBlank()) {
            log.warn("여론 요약 텍스트가 비어 있어 저장하지 않습니다: sessionId={}", sessionId);
            return false;
        }
        if (mapper.findLatestSummaryMessage(sessionId).filter(message::equals).isPresent()) {
            log.debug("직전과 같은 여론 요약이라 저장을 건너뜁니다: sessionId={}", sessionId);
            return false;
        }
        long briefingId = mapper.insertChatSummaryBriefing(sessionId, message);
        log.debug("여론 요약 브리핑 저장: sessionId={} briefingId={}", sessionId, briefingId);
        return true;
    }
}
