package com.example.laive.broadcast.session.service;

import com.example.laive.broadcast.session.config.BroadcastSessionProperties;
import com.example.laive.broadcast.session.exception.BroadcastSessionException;
import com.example.laive.streamer.risk.domain.RiskTermKind;
import com.example.laive.streamer.risk.domain.RiskTermSeverity;
import com.example.laive.streamer.risk.domain.StreamerRiskTerm;
import com.example.laive.streamer.risk.service.StreamerRiskTermService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

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

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private BroadcastSessionQueryService queryService;
    private StreamerRiskTermService riskTermService;
    private VoiceTicketService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        queryService = mock(BroadcastSessionQueryService.class);
        riskTermService = mock(StreamerRiskTermService.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(riskTermService.list(anyLong())).thenReturn(List.of());
        service = new VoiceTicketService(
                redisTemplate, queryService, properties(), riskTermService, MAPPER);
    }

    /**
     * AI 서버(Python)의 {@code app/auth.py}가 이 JSON을 파싱한다.
     * 필드 이름이 어긋나면 모든 음성 접속이 조용히 거절되므로 키와 값을 함께 검증한다.
     */
    @Test
    void storesStreamerAndSessionAsJson() throws Exception {
        String ticket = service.issue(7L, 4242L);

        assertThat(ticket).isNotBlank();
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(key.capture(), value.capture(), any(Duration.class));

        assertThat(key.getValue()).isEqualTo("voice:ticket:" + ticket);

        JsonNode stored = MAPPER.readTree(value.getValue());
        assertThat(stored.get("streamerId").asLong()).isEqualTo(7L);
        assertThat(stored.get("sessionId").asLong()).isEqualTo(4242L);
        assertThat(stored.get("terms").get("block")).isEmpty();
        assertThat(stored.get("terms").get("allow")).isEmpty();
    }

    /**
     * 개인 사전이 티켓에 실려야 AI 서버가 스트리머별 automaton을 만들 수 있다.
     * 허용어는 문자열 목록, 금칙어는 등급이 함께 붙은 객체 목록이다.
     */
    @Test
    void embedsPersonalDictionary() throws Exception {
        when(riskTermService.list(7L)).thenReturn(List.of(
                new StreamerRiskTerm(1L, RiskTermKind.BLOCK, "개새기", "개새기", RiskTermSeverity.HIGH),
                new StreamerRiskTerm(2L, RiskTermKind.ALLOW, "씨발", "씨발", null)
        ));

        service.issue(7L, 4242L);

        JsonNode terms = MAPPER.readTree(captureStoredValue()).get("terms");
        assertThat(terms.get("block").get(0).get("term").asText()).isEqualTo("개새기");
        assertThat(terms.get("block").get(0).get("severity").asText()).isEqualTo("HIGH");
        assertThat(terms.get("allow").get(0).asText()).isEqualTo("씨발");
    }

    /** 등급을 고르지 않은 금칙어는 MEDIUM으로 나간다. AI 쪽에 NULL을 넘기지 않는다. */
    @Test
    void defaultsBlockSeverityToMedium() throws Exception {
        when(riskTermService.list(7L)).thenReturn(List.of(
                new StreamerRiskTerm(1L, RiskTermKind.BLOCK, "개새기", "개새기", null)
        ));

        service.issue(7L, 4242L);

        JsonNode block = MAPPER.readTree(captureStoredValue()).get("terms").get("block");
        assertThat(block.get(0).get("severity").asText()).isEqualTo("MEDIUM");
    }

    /**
     * 사전 조회가 실패해도 방송은 시작될 수 있어야 한다.
     * 기본 감지는 사전 없이도 정상 동작한다.
     */
    @Test
    void issuesTicketEvenIfDictionaryLookupFails() throws Exception {
        when(riskTermService.list(7L)).thenThrow(new IllegalStateException("DB down"));

        String ticket = service.issue(7L, 4242L);

        assertThat(ticket).isNotBlank();
        JsonNode terms = MAPPER.readTree(captureStoredValue()).get("terms");
        assertThat(terms.get("block")).isEmpty();
        assertThat(terms.get("allow")).isEmpty();
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

    private String captureStoredValue() {
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(anyString(), value.capture(), any(Duration.class));
        return value.getValue();
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
