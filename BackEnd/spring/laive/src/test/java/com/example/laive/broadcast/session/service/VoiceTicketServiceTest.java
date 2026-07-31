package com.example.laive.broadcast.session.service;

import com.example.laive.broadcast.session.config.BroadcastSessionProperties;
import com.example.laive.broadcast.session.exception.BroadcastSessionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoiceTicketServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private BroadcastSessionQueryService queryService;
    private VoiceTicketService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        queryService = mock(BroadcastSessionQueryService.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new VoiceTicketService(redisTemplate, queryService, properties());
    }

    /**
     * AI 서버(Python)가 {@code streamerId|sessionId}로 파싱한다.
     * 이 형식이 어긋나면 모든 음성 접속이 조용히 거절되므로 값까지 검증한다.
     */
    @Test
    void storesStreamerAndSessionSeparatedByPipe() {
        String ticket = service.issue(7L, 4242L);

        assertThat(ticket).isNotBlank();
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(key.capture(), value.capture(), any(Duration.class));

        assertThat(key.getValue()).isEqualTo("voice:ticket:" + ticket);
        assertThat(value.getValue()).isEqualTo("7|4242");
    }

    @Test
    void usesConfiguredTtl() {
        service.issue(1L, 2L);
        verify(valueOperations).set(anyString(), anyString(), eq(Duration.ofSeconds(60)));
    }

    /** 티켓은 URL·JSON에 그대로 실리므로 구분자나 특수문자가 섞이면 안 된다. */
    @Test
    void ticketIsUrlSafe() {
        assertThat(service.issue(1L, 2L)).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void issuesDifferentTicketEachTime() {
        assertThat(service.issue(1L, 2L)).isNotEqualTo(service.issue(1L, 2L));
    }

    /**
     * 남의 세션 번호로는 티켓이 나오면 안 된다 — 이게 막히지 않으면
     * 티켓을 도입한 의미가 없다.
     */
    @Test
    void rejectsSessionOwnedByAnotherStreamer() {
        when(queryService.getSession(anyLong(), anyLong()))
                .thenThrow(BroadcastSessionException.notFound());

        assertThatThrownBy(() -> service.issue(999L, 4242L))
                .isInstanceOf(BroadcastSessionException.class);
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    private BroadcastSessionProperties properties() {
        return new BroadcastSessionProperties(
                Duration.ofSeconds(10),
                Duration.ofSeconds(30),
                Duration.ofMinutes(30),
                Duration.ofSeconds(60),
                new BroadcastSessionProperties.Outbox(
                        500L, 50, Duration.ofSeconds(30), 8, Duration.ofMinutes(1))
        );
    }
}
