package com.example.laive.broadcast.questions.service;

import com.example.laive.broadcast.questions.config.RepeatedQuestionProperties;
import com.example.laive.broadcast.questions.repository.mybatis.RepeatedQuestionMapper;
import com.example.laive.broadcast.realtime.domain.ChatAiQuestionsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 폴링 응답 1건을 DB에 반영한다. HTTP는 모른다 — 호출 경계는
 * {@code RepeatedQuestionPollService}가 가진다.
 *
 * <p>순서가 계약이다(§3 ①②③): UPSERT가 askers를 이번 응답 값으로 만든 뒤에
 * baseline을 확정해야 하고, 리셋 방어가 baseline을 낮춘 뒤에 재노출을 판정해야
 * 방어된 행이 diff 0으로 자연히 건너뛰어진다("이번 판정 스킵").
 */
@Service
public class RepeatedQuestionSyncService {

    private static final Logger log = LoggerFactory.getLogger(RepeatedQuestionSyncService.class);

    private final RepeatedQuestionMapper mapper;
    private final RepeatedQuestionProperties properties;

    public RepeatedQuestionSyncService(RepeatedQuestionMapper mapper, RepeatedQuestionProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    /**
     * @param now 판정 기준 시각. 폴러가 주입한다 — 테스트가 유예 경과를 이 값으로 몬다
     */
    @Transactional
    public void apply(long sessionId, ChatAiQuestionsResult result, Instant now) {
        List<Long> responseIds = result.questions().stream()
                .map(ChatAiQuestionsResult.Question::questionId)
                .toList();
        Instant graceCutoff = now.minus(properties.dismissGrace());

        if (!result.questions().isEmpty()) {
            mapper.upsertQuestions(sessionId, now, result.questions());
            mapper.confirmBaselineFromResponse(sessionId, graceCutoff, responseIds, now);
        }
        // 빈 응답이어도 돈다 — 모든 dismissed 질문이 "응답에 없음" 경로다.
        mapper.confirmBaselineByFallback(sessionId, graceCutoff, responseIds, now);
        mapper.lowerBaselineOnShrink(sessionId, now);
        int reexposed = mapper.reexpose(sessionId, properties.reexposeThreshold(), now);
        if (reexposed > 0) {
            log.info("재질문 감지로 {}건을 다시 노출합니다(답변이 시청자에게 닿지 않은 신호): sessionId={}",
                    reexposed, sessionId);
        }
    }
}
