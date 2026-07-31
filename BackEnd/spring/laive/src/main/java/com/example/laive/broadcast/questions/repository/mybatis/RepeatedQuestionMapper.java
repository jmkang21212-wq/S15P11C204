package com.example.laive.broadcast.questions.repository.mybatis;

import com.example.laive.broadcast.questions.domain.RepeatedQuestionView;
import com.example.laive.broadcast.realtime.domain.ChatAiQuestionsResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * PostgreSQL 전용 SQL은 resources/mapper/broadcast/RepeatedQuestionMapper.xml에 둔다.
 *
 * <p>재질문 감지 3컬럼 알고리즘(계약 §3 ①②③)의 각 단계가 쿼리 하나씩이다.
 * 호출 순서는 {@code RepeatedQuestionSyncService}가 고정한다:
 * UPSERT → ② baseline 확정(응답분 → fallback) → ③ 리셋 방어 → ③ 재노출.
 */
@Mapper
public interface RepeatedQuestionMapper {

    /** 폴링 대상 세션. PREPARING의 404는 계약상 정상이라 함께 돌린다. */
    List<Long> findPollableSessionIds();

    /**
     * 폴링 응답을 (session_id, question_id)로 UPSERT한다. 값이 같은 행은 건드리지
     * 않아(IS DISTINCT FROM) 15초 폴링에도 죽은 튜플이 쌓이지 않는다.
     * dismiss 관련 컬럼과 first_asked_at은 절대 갱신하지 않는다.
     */
    int upsertQuestions(
            @Param("sessionId") long sessionId,
            @Param("now") Instant now,
            @Param("items") List<ChatAiQuestionsResult.Question> items
    );

    /**
     * ② 유예가 끝난 dismissed 질문의 baseline을 이번 응답의 askers로 확정한다.
     * UPSERT 직후에 불러야 한다 — askers 컬럼이 이미 이번 응답 값이다.
     * questionIds가 비면 호출하지 말 것(호출자가 생략한다).
     */
    int confirmBaselineFromResponse(
            @Param("sessionId") long sessionId,
            @Param("graceCutoff") Instant graceCutoff,
            @Param("questionIds") List<Long> questionIds,
            @Param("now") Instant now
    );

    /**
     * ② fallback: 유예가 끝났는데 응답에 없는(top-N 밖으로 밀린) 질문은
     * askers_at_dismiss로 확정한다. 과계산이지만 재노출 임계가 흡수한다 —
     * 미확정으로 두면 나중에 급증한 askers가 baseline이 되어 정작 감지할 급증을 놓친다.
     */
    int confirmBaselineByFallback(
            @Param("sessionId") long sessionId,
            @Param("graceCutoff") Instant graceCutoff,
            @Param("questionIds") List<Long> questionIds,
            @Param("now") Instant now
    );

    /**
     * ③ 집계 리셋 방어: askers가 baseline보다 줄었으면 baseline을 현재값으로 낮춘다.
     * 낮아진 행은 diff가 0이라 이어지는 재노출 판정을 자연히 건너뛴다.
     */
    int lowerBaselineOnShrink(@Param("sessionId") long sessionId, @Param("now") Instant now);

    /**
     * ③ 재노출: baseline 대비 askers가 임계 이상 늘었으면 다시 노출한다.
     * dismiss 3컬럼은 초기화하고 reexposed_at만 남긴다 — 재차 dismiss가 ①을 새로 시작한다.
     */
    int reexpose(
            @Param("sessionId") long sessionId,
            @Param("threshold") int threshold,
            @Param("now") Instant now
    );

    /**
     * ① 답변 완료. 이미 dismissed면 0행 — 중복 클릭이 유예 타이머를 재시작하지 못한다.
     */
    int dismiss(
            @Param("sessionId") long sessionId,
            @Param("questionId") long questionId,
            @Param("now") Instant now
    );

    /** dismiss가 0행일 때 404(미존재)와 200(이미 처리됨)을 가르는 데 쓴다. */
    boolean existsQuestion(@Param("sessionId") long sessionId, @Param("questionId") long questionId);

    /** 멱등 재호출 응답용 첫 처리 시각. 행이 없거나 아직 미처리면 null. */
    Instant findDismissedAt(@Param("sessionId") long sessionId, @Param("questionId") long questionId);

    /** 콘솔 노출분: dismissed 제외, askers 내림차순. */
    List<RepeatedQuestionView> findVisible(@Param("sessionId") long sessionId, @Param("limit") int limit);
}
