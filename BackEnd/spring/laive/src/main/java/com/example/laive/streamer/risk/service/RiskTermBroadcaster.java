package com.example.laive.streamer.risk.service;

import com.example.laive.streamer.risk.domain.RiskTermsPayload;
import com.example.laive.streamer.risk.domain.StreamerRiskTerm;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * 개인 사전 변경을 방송 중인 AI 세션에 알린다 (AI/RISK_POLICY_SPEC.md §3).
 *
 * <p><b>왜 Redis pub/sub인가</b> — AI 서버는 티켓 검증 때문에 이미 같은 Redis에 붙어 있고
 * Spring도 마찬가지다. 그래서 새 인증 표면을 만들지 않고 Spring→AI 방향 채널을 얻을 수
 * 있다. AI에 inbound 제어 API를 열면 {@code /internal/**}처럼 토큰을 또 하나 관리해야 한다.
 *
 * <p><b>전달 보장이 없다.</b> Redis pub/sub은 구독자가 없거나 끊겨 있으면 메시지를 버린다.
 * 놓쳐도 다음 방송에는 티켓으로 정상 반영되므로 재전송이나 ACK를 만들지 않는다. 발행
 * 실패가 등록 자체를 실패시켜서도 안 된다 — 사전은 이미 DB에 저장됐고, 그것이 정본이다.
 */
@Component
public class RiskTermBroadcaster {

    /** AI 서버의 구독 채널과 반드시 같아야 한다 (app/detect 쪽 subscriber). */
    private static final String CHANNEL_PREFIX = "voice:terms:";

    private static final Logger log = LoggerFactory.getLogger(RiskTermBroadcaster.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RiskTermBroadcaster(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 트랜잭션이 커밋된 뒤에 발행한다.
     *
     * <p>커밋 전에 보내면 AI 서버가 아직 보이지 않는 상태를 읽어가고, 롤백되면 있지도 않은
     * 사전이 방송에 적용된 채로 남는다. 트랜잭션 밖에서 호출되면(테스트 등) 즉시 보낸다.
     */
    public void publishAfterCommit(long streamerId, List<StreamerRiskTerm> terms) {
        RiskTermsPayload payload = RiskTermsPayload.from(terms);

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish(streamerId, payload);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish(streamerId, payload);
            }
        });
    }

    private void publish(long streamerId, RiskTermsPayload payload) {
        try {
            redisTemplate.convertAndSend(
                    CHANNEL_PREFIX + streamerId,
                    objectMapper.writeValueAsString(new TermsMessage(streamerId, payload))
            );
        } catch (JsonProcessingException | RuntimeException e) {
            // 삼켜야 한다. 이 발행은 "지금 방송에도 반영"이라는 편의 기능이고, 실패해도
            // 다음 방송에는 티켓으로 올바르게 전달된다. 등록 API를 실패시킬 이유가 없다.
            log.warn("개인 사전 변경 발행 실패 streamerId={} — 다음 방송부터 반영된다: {}",
                    streamerId, e.toString());
        }
    }

    /**
     * pub/sub 메시지 봉투. <b>필드 이름이 곧 AI 서버와의 계약이다.</b>
     *
     * <p>AI 서버는 {@code PersonalTerms.parse_json}에서 {@code data.get("terms")}를 읽는다
     * (AI/app/detect/personal.py). 그래서 payload를 최상위에 그대로 실으면 {@code None}이
     * 되고, {@code parse}가 dict가 아닌 입력을 빈 사전으로 처리하도록 설계돼 있어 <b>실패가
     * 조용히 삼켜진다</b> — 방송 중 변경이 사전을 통째로 비우면서 오류도 로그도 남지 않았다.
     *
     * <p>{@code streamerId}는 채널명에 이미 있어 필수는 아니지만,
     * {@code VoiceTicketService.TicketValue}와 같은 모양으로 맞춰 둔다. 두 경로가 같은
     * 봉투를 쓰면 AI 쪽 파싱이 하나로 유지되고, 한쪽만 바꿨을 때 이번 같은 어긋남이 생기지
     * 않는다.
     */
    private record TermsMessage(long streamerId, RiskTermsPayload terms) {
    }
}
