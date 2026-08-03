package com.example.laive.broadcast.session.service;

import com.example.laive.broadcast.session.config.BroadcastSessionProperties;
import com.example.laive.streamer.risk.domain.RiskTermsPayload;
import com.example.laive.streamer.risk.service.StreamerRiskTermService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 음성 AI 서버(WebSocket) 접속용 일회용 티켓을 발급한다.
 *
 * <p>WebSocket은 브라우저에서 Authorization 헤더를 실을 수 없어 JWT를 그대로 쓸 수 없다.
 * 그래서 이미 인증된 HTTP 요청으로 짧은 티켓을 받아 가고, AI 서버는 Redis에서 그 티켓을
 * 조회해 <b>어느 방송 세션인지</b>를 알아낸다.
 *
 * <p>핵심은 AI 서버가 세션 번호를 <b>티켓에서</b> 얻는다는 점이다. 이전에는 클라이언트가
 * 핸드셰이크에 실어 보낸 값을 그대로 믿어서, 아무 번호나 넣으면 남의 방송에 전사·위험
 * 이벤트를 기록할 수 있었다. 티켓은 이 서비스가 소유권을 확인한 뒤에만 발급되므로
 * 그 경로가 막힌다.
 *
 * <p>개인 사전도 같은 이유로 여기에 싣는다. 핸드셰이크로 받으면 조작된 허용어로 감지를
 * 무력화할 수 있다. 종류당 200개 상한이 있어 최악의 경우도 16KB 남짓이라, 사전 조회용
 * 내부 API를 따로 두지 않고 티켓에 그대로 담는다 (AI/RISK_POLICY_SPEC.md §3).
 *
 * <p><b>⚠ 저장 형식이 JSON이다. AI 서버의 {@code app/auth.py}와 짝을 맞춰야 한다.</b>
 * 예전에는 {@code "streamerId|sessionId"} 파이프 문자열이었고, AI 쪽은 전환기 동안 두
 * 형식을 모두 받는다. 한쪽만 배포해도 접속이 조용히 전부 거절되므로(파싱 실패는 로그만
 * 남기고 None을 돌려준다) 양쪽을 함께 내보내야 한다.
 */
@Service
public class VoiceTicketService {

    /** Redis DB 0(Spring 영역)의 키. AI 서버도 같은 접두사로 조회한다. */
    private static final String KEY_PREFIX = "voice:ticket:";

    private static final Logger log = LoggerFactory.getLogger(VoiceTicketService.class);

    private final StringRedisTemplate redisTemplate;
    private final BroadcastSessionQueryService queryService;
    private final BroadcastSessionProperties properties;
    private final StreamerRiskTermService riskTermService;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    public VoiceTicketService(
            StringRedisTemplate redisTemplate,
            BroadcastSessionQueryService queryService,
            BroadcastSessionProperties properties,
            StreamerRiskTermService riskTermService,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.queryService = queryService;
        this.properties = properties;
        this.riskTermService = riskTermService;
        this.objectMapper = objectMapper;
    }

    /**
     * 티켓을 발급한다. 세션이 없거나 남의 것이면 예외가 난다.
     *
     * @return AI 서버 핸드셰이크에 실을 티켓 문자열
     */
    public String issue(long streamerId, long sessionId) {
        // 소유권 확인. 조회가 streamerId로 필터링되므로 남의 세션이면 notFound가 난다
        // (존재 자체를 숨기는 기존 정책과 같다).
        queryService.getSession(streamerId, sessionId);

        byte[] buffer = new byte[32];
        random.nextBytes(buffer);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);

        redisTemplate.opsForValue().set(
                KEY_PREFIX + ticket,
                serialize(streamerId, sessionId),
                properties.voiceTicketTtl()
        );
        return ticket;
    }

    private String serialize(long streamerId, long sessionId) {
        try {
            return objectMapper.writeValueAsString(
                    new TicketValue(streamerId, sessionId, loadTerms(streamerId)));
        } catch (JsonProcessingException e) {
            // 여기서 실패하면 티켓을 만들 수 없다. 사전을 빼고 발급하는 대신 실패시킨다 —
            // 조용히 기본 사전으로 방송이 시작되면 스트리머는 설정이 반영된 줄 알고 있다가
            // 방송 내내 잘못된 경고를 받는다.
            throw new IllegalStateException("음성 티켓 직렬화에 실패했습니다.", e);
        }
    }

    /**
     * 개인 사전을 읽는다. 실패해도 티켓 발급 자체는 막지 않는다.
     *
     * <p>사전 조회가 방송 시작을 막는 것은 과하다. 사전 없이도 기본 감지는 정상 동작하고,
     * 스트리머는 방송을 시작할 수 있어야 한다.
     */
    private RiskTermsPayload loadTerms(long streamerId) {
        try {
            return RiskTermsPayload.from(riskTermService.list(streamerId));
        } catch (RuntimeException e) {
            log.warn("개인 사전 조회 실패 streamerId={} — 기본 사전으로 방송을 시작한다: {}",
                    streamerId, e.toString());
            return RiskTermsPayload.empty();
        }
    }

    /** Redis에 저장되는 티켓 값. 필드 이름이 곧 AI 서버와의 계약이다. */
    private record TicketValue(long streamerId, long sessionId, RiskTermsPayload terms) {
    }
}
