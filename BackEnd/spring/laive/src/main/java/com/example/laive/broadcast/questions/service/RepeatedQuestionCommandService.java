package com.example.laive.broadcast.questions.service;

import com.example.laive.broadcast.questions.dto.response.RepeatedQuestionDismissResponse;
import com.example.laive.broadcast.questions.exception.RepeatedQuestionException;
import com.example.laive.broadcast.questions.repository.mybatis.RepeatedQuestionMapper;
import com.example.laive.broadcast.session.service.BroadcastSessionQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * 스트리머의 "답변 완료" 처리다. AI는 dismissed를 모른 채 계속 세고, Spring의
 * 이 기록이 재질문 감지(계약 §3 ①)의 시작점이 된다.
 */
@Service
public class RepeatedQuestionCommandService {

    private final BroadcastSessionQueryService sessionQueryService;
    private final RepeatedQuestionMapper mapper;
    private final Clock clock;

    public RepeatedQuestionCommandService(
            BroadcastSessionQueryService sessionQueryService,
            RepeatedQuestionMapper mapper,
            Clock clock
    ) {
        this.sessionQueryService = sessionQueryService;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * 멱등이다: 이미 처리된 질문에 다시 호출해도 첫 처리의 시각과 askers_at_dismiss가
     * 보존된다(유예 타이머 재시작 방지). 재노출된 질문의 재처리는 정상 경로로 ①을
     * 새로 시작한다.
     */
    @Transactional
    public RepeatedQuestionDismissResponse dismiss(long streamerId, long sessionId, long questionId) {
        // 세션 조회가 소유권 검증을 겸한다 — 타인 세션은 404로 숨겨진다.
        sessionQueryService.getSession(streamerId, sessionId);

        Instant now = clock.instant();
        int updated = mapper.dismiss(sessionId, questionId, now);
        if (updated == 1) {
            return new RepeatedQuestionDismissResponse(questionId, now);
        }
        if (!mapper.existsQuestion(sessionId, questionId)) {
            throw RepeatedQuestionException.notFound();
        }
        Instant firstDismissedAt = mapper.findDismissedAt(sessionId, questionId);
        return new RepeatedQuestionDismissResponse(questionId, firstDismissedAt != null ? firstDismissedAt : now);
    }
}
