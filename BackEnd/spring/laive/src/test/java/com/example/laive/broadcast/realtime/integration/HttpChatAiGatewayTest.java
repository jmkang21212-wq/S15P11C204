package com.example.laive.broadcast.realtime.integration;

import com.example.laive.broadcast.realtime.config.ChatAiProperties;
import com.example.laive.broadcast.realtime.domain.ChatAiIngestResult;
import com.example.laive.broadcast.realtime.domain.IngestChat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * chat-ai 응답 계약(CHAT_AI_API.md)의 변환 — 한국어 라벨 → 코드, 0건 윈도우의
 * null 눌러쓰기, 실패 격리 — 를 검증한다.
 */
class HttpChatAiGatewayTest {

    private static final Instant SENT_AT = Instant.parse("2026-07-21T12:00:00Z");

    private MockRestServiceServer server;
    private HttpChatAiGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://chat-ai:8001");
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new HttpChatAiGateway(
                builder.build(),
                new ChatAiProperties("http://chat-ai:8001", "test-token", Duration.ofSeconds(2))
        );
    }

    @Test
    void mapsKoreanLabelsToCodesInRequestOrder() {
        server.expect(requestTo("http://chat-ai:8001/broadcasts/9002/ingest"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(jsonPath("$.viewers").value(2417))
                .andExpect(jsonPath("$.chats[0].uid").value("hash-a"))
                .andRespond(withSuccess("""
                        {
                          "results": [
                            {"label": "웃음/재미", "score": 0.94},
                            {"label": "혼란/질문", "score": 0.91}
                          ],
                          "window": {
                            "windowSec": 180,
                            "chatCount": 6185,
                            "uniqueChatters": 1168,
                            "viewers": 2417,
                            "engagement": 0.214,
                            "distribution": {"웃음/재미": 0.6, "중립": 0.4},
                            "dominant": "웃음/재미"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<ChatAiIngestResult> result = gateway.ingest(9002L, 2417, List.of(
                new IngestChat("hash-a", "ㅋㅋㅋ 개웃기네", SENT_AT),
                new IngestChat("hash-b", "오늘 몇시까지 해요?", SENT_AT.plusMillis(300))
        ));

        ChatAiIngestResult ingest = result.orElseThrow();
        assertThat(ingest.labels()).containsExactly("JOY", "CONFUSION");
        assertThat(ingest.window().analyzedChat()).isEqualTo(6185);
        assertThat(ingest.window().dominant()).isEqualTo("JOY");
        assertThat(ingest.window().distribution())
                .containsEntry("JOY", 0.6)
                .containsEntry("NEUTRAL", 0.4);
    }

    @Test
    void unknownLabelBecomesNullWithoutFailingBatch() {
        server.expect(requestTo("http://chat-ai:8001/broadcasts/1/ingest"))
                .andRespond(withSuccess("""
                        {
                          "results": [{"label": "새로운감정", "score": 0.9}],
                          "window": null
                        }
                        """, MediaType.APPLICATION_JSON));

        ChatAiIngestResult result = gateway
                .ingest(1L, null, List.of(new IngestChat("h", "msg", SENT_AT)))
                .orElseThrow();

        assertThat(result.labels()).containsExactly((String) null);
        assertThat(result.window()).isNull();
    }

    @Test
    void zeroChatWindowGetsNullDominantAndDistribution() {
        // chat-ai는 0건일 때 전 라벨 0.0 분포를 주지만 명세 10.6은 null을 요구한다.
        server.expect(requestTo("http://chat-ai:8001/broadcasts/1/ingest"))
                .andRespond(withSuccess("""
                        {
                          "results": [],
                          "window": {
                            "windowSec": 180,
                            "chatCount": 0,
                            "uniqueChatters": 0,
                            "viewers": 100,
                            "engagement": null,
                            "distribution": {"웃음/재미": 0.0, "중립": 0.0},
                            "dominant": null
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        ChatAiIngestResult result = gateway.ingest(1L, 100, List.of()).orElseThrow();

        assertThat(result.window().analyzedChat()).isZero();
        assertThat(result.window().dominant()).isNull();
        assertThat(result.window().distribution()).isNull();
    }

    @Test
    void serverErrorSkipsBatchInsteadOfThrowing() {
        // 실패가 여기서 새면 채팅 저장 트랜잭션과 멱등 선점까지 되돌아간다.
        server.expect(requestTo("http://chat-ai:8001/broadcasts/1/ingest"))
                .andRespond(withServerError());

        Optional<ChatAiIngestResult> result =
                gateway.ingest(1L, 100, List.of(new IngestChat("h", "msg", SENT_AT)));

        assertThat(result).isEmpty();
    }

    @Test
    void resultCountMismatchFillsMissingLabelsWithNull() {
        server.expect(requestTo("http://chat-ai:8001/broadcasts/1/ingest"))
                .andRespond(withSuccess("""
                        {
                          "results": [{"label": "중립", "score": 0.8}],
                          "window": null
                        }
                        """, MediaType.APPLICATION_JSON));

        ChatAiIngestResult result = gateway.ingest(1L, 100, List.of(
                new IngestChat("h1", "하나", SENT_AT),
                new IngestChat("h2", "둘", SENT_AT)
        )).orElseThrow();

        assertThat(result.labels()).containsExactly("NEUTRAL", null);
    }

    @Test
    void endBroadcastSwallowsFailure() {
        server.expect(requestTo("http://chat-ai:8001/broadcasts/9002"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        gateway.endBroadcast(9002L); // 예외가 새지 않아야 한다
    }

    private static org.springframework.test.web.client.ResponseCreator withStatus(HttpStatus status) {
        return org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(status);
    }
}
