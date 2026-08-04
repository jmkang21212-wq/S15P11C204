package com.example.laive.broadcast.realtime.domain;

import java.time.Instant;

/**
 * 여론 요약 생성 호출(POST /summary)의 결과다(계약 §2).
 *
 * <p>다른 게이트웨이 메서드처럼 Optional로 눌러쓰지 않는 이유: 409·422 거절은
 * 실패가 아니라 본문(retryAfterSec 등)을 가진 정상 응답이고, 수동 버튼은 그
 * 본문으로 카운트다운을 그려야 한다. sealed라 호출자의 switch가 누락 없이
 * 전 경우를 다루도록 컴파일러가 강제한다.
 */
public sealed interface ChatAiSummaryCall {

    /** 새 요약이 생성됐다. */
    record Success(ChatAiSummary summary) implements ChatAiSummaryCall {
    }

    /**
     * 마지막 요약 후 60초 이내(계약: 정상 거절, 오류 로그 금지).
     *
     * @param retryAfterSec 쿨다운 해제까지 남은 초
     * @param lastSummaryAt 마지막 요약 생성 시각. chat-ai 캐시에 그 요약이 살아 있다
     */
    record Cooldown(int retryAfterSec, Instant lastSummaryAt) implements ChatAiSummaryCall {
    }

    /** 다른 호출자가 생성 중(계약: POST 재시도 금지, 잠시 뒤 GET으로 회수). */
    record InFlight() implements ChatAiSummaryCall {
    }

    /** 윈도우 채팅이 최소 건수 미만(계약: 정상 거절). */
    record NotEnoughChats(long chatCount, int required) implements ChatAiSummaryCall {
    }

    /** ingest가 들어온 적 없는 방송(계약: PREPARING 폴링의 정상 경로). */
    record UnknownBroadcast() implements ChatAiSummaryCall {
    }

    /** 전송 실패·타임아웃·5xx(LLM 미설정/실패 포함)·no-op 게이트웨이. */
    record Failure() implements ChatAiSummaryCall {
    }
}
