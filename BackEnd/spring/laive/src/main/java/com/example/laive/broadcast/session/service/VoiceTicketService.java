package com.example.laive.broadcast.session.service;

import com.example.laive.broadcast.session.config.BroadcastSessionProperties;
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
 * <p>저장 형식은 {@code PlatformLinkStateService}와 같은 파이프 구분 문자열이다.
 * AI 서버(Python)가 읽어야 하므로 Java 직렬화를 쓰지 않는다.
 */
@Service
public class VoiceTicketService {

    /** Redis DB 0(Spring 영역)의 키. AI 서버도 같은 접두사로 조회한다. */
    private static final String KEY_PREFIX = "voice:ticket:";
    private static final String DELIMITER = "|";

    private final StringRedisTemplate redisTemplate;
    private final BroadcastSessionQueryService queryService;
    private final BroadcastSessionProperties properties;
    private final SecureRandom random = new SecureRandom();

    public VoiceTicketService(
            StringRedisTemplate redisTemplate,
            BroadcastSessionQueryService queryService,
            BroadcastSessionProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.queryService = queryService;
        this.properties = properties;
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
                streamerId + DELIMITER + sessionId,
                properties.voiceTicketTtl()
        );
        return ticket;
    }
}
