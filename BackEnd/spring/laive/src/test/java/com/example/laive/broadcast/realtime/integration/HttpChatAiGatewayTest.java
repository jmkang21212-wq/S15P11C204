package com.example.laive.broadcast.realtime.integration;

import com.example.laive.broadcast.realtime.config.ChatAiProperties;
import com.example.laive.broadcast.realtime.domain.ChatAiIngestResult;
import com.example.laive.broadcast.realtime.domain.ChatAiQuestionsResult;
import com.example.laive.broadcast.realtime.domain.ChatAiSummary;
import com.example.laive.broadcast.realtime.domain.ChatAiSummaryCall;
import com.example.laive.broadcast.realtime.domain.IngestChat;
import com.example.laive.broadcast.realtime.domain.SummaryMode;
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
        RestClient restClient = builder.build();
        gateway = new HttpChatAiGateway(
                restClient,
                restClient, // 테스트에서는 Timeout 차이가 없으므로 같은 클라이언트를 쓴다
                new ChatAiProperties("http://chat-ai:8001", "test-token",
                        Duration.ofSeconds(2), Duration.ofSeconds(15))
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
    void mapsQuestionsResponseToDomain() {
        server.expect(requestTo("http://chat-ai:8001/broadcasts/9002/questions?limit=50"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("""
                        {
                          "questions": [
                            {"questionId": 1753690000000007, "text": "오늘 몇 시까지 하세요?", "type": "FAQ",
                             "askers": 42, "messages": 51,
                             "firstAskedAt": 1753690000123, "lastAskedAt": 1753693400555},
                            {"questionId": 1753690000000012, "text": "저게 안맞아?", "type": null,
                             "askers": 8, "messages": 9,
                             "firstAskedAt": 1753693000000, "lastAskedAt": 1753693400000}
                          ],
                          "totalQuestions": 1284,
                          "pendingEmbedding": 23,
                          "updatedAt": 1753693410000
                        }
                        """, MediaType.APPLICATION_JSON));

        ChatAiQuestionsResult result = gateway.getQuestions(9002L, 50).orElseThrow();

        assertThat(result.totalQuestions()).isEqualTo(1284);
        assertThat(result.pendingEmbedding()).isEqualTo(23);
        assertThat(result.updatedAt()).isEqualTo(Instant.ofEpochMilli(1753693410000L));
        assertThat(result.questions()).hasSize(2);
        ChatAiQuestionsResult.Question first = result.questions().get(0);
        assertThat(first.questionId()).isEqualTo(1753690000000007L); // 16자리 — long이어야 한다
        assertThat(first.text()).isEqualTo("오늘 몇 시까지 하세요?");
        assertThat(first.type()).isEqualTo("FAQ");
        assertThat(first.askers()).isEqualTo(42);
        assertThat(first.firstAskedAt()).isEqualTo(Instant.ofEpochMilli(1753690000123L));
        assertThat(result.questions().get(1).type()).isNull(); // 판정 전 null 통과
    }

    @Test
    void unknownQuestionTypeBecomesNullAndBrokenItemIsDropped() {
        server.expect(requestTo("http://chat-ai:8001/broadcasts/1/questions?limit=5"))
                .andRespond(withSuccess("""
                        {
                          "questions": [
                            {"questionId": 1753690000000001, "text": "질문", "type": "새타입",
                             "askers": 5, "messages": 6,
                             "firstAskedAt": 1, "lastAskedAt": 2},
                            {"questionId": null, "text": "필수 필드 없음", "type": "FAQ",
                             "askers": 3, "messages": 3,
                             "firstAskedAt": 1, "lastAskedAt": 2}
                          ],
                          "totalQuestions": 2,
                          "pendingEmbedding": 0,
                          "updatedAt": 3
                        }
                        """, MediaType.APPLICATION_JSON));

        ChatAiQuestionsResult result = gateway.getQuestions(1L, 5).orElseThrow();

        assertThat(result.questions()).hasSize(1); // 깨진 항목만 버리고 나머지는 산다
        assertThat(result.questions().get(0).type()).isNull(); // 모르는 타입은 판정 전과 동일 취급
    }

    @Test
    void questionsNotFoundMeansNoIngestYetAndReturnsEmpty() {
        // 계약: 404 = ingest가 들어온 적 없는 방송(PREPARING 폴링의 정상 경로).
        server.expect(requestTo("http://chat-ai:8001/broadcasts/1/questions?limit=50"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(gateway.getQuestions(1L, 50)).isEmpty();
    }

    @Test
    void questionsServerErrorSkipsCycleInsteadOfThrowing() {
        server.expect(requestTo("http://chat-ai:8001/broadcasts/1/questions?limit=50"))
                .andRespond(withServerError());

        assertThat(gateway.getQuestions(1L, 50)).isEmpty();
    }

    @Test
    void summarySuccessMapsKoreanLabelsAndInstants() {
        server.expect(requestTo("http://chat-ai:8001/broadcasts/9002/summary"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(jsonPath("$.mode").value("MANUAL"))
                .andRespond(withSuccess("""
                        {
                          "summary": "신곡 무대에 환호가 집중되고 있습니다.",
                          "dominant": "환호/감탄",
                          "distribution": {"환호/감탄": 0.34, "웃음/재미": 0.22},
                          "chatCount": 4210,
                          "windowSec": 180,
                          "generatedAt": "2026-07-29T21:03:00Z",
                          "nextAvailableAt": "2026-07-29T21:04:00Z"
                        }
                        """, MediaType.APPLICATION_JSON));

        ChatAiSummaryCall call = gateway.requestSummary(9002L, SummaryMode.MANUAL);

        ChatAiSummary summary = ((ChatAiSummaryCall.Success) call).summary();
        assertThat(summary.summary()).isEqualTo("신곡 무대에 환호가 집중되고 있습니다.");
        assertThat(summary.dominant()).isEqualTo("EXCITEMENT");
        assertThat(summary.distribution())
                .containsEntry("EXCITEMENT", 0.34)
                .containsEntry("JOY", 0.22);
        assertThat(summary.chatCount()).isEqualTo(4210);
        assertThat(summary.generatedAt()).isEqualTo(Instant.parse("2026-07-29T21:03:00Z"));
        assertThat(summary.nextAvailableAt()).isEqualTo(Instant.parse("2026-07-29T21:04:00Z"));
    }

    @Test
    void summaryCooldownRejectionCarriesRetryAfter() {
        // 계약: 409 COOLDOWN은 오류가 아니라 본문을 가진 정상 거절이다.
        server.expect(requestTo("http://chat-ai:8001/broadcasts/1/summary"))
                .andExpect(jsonPath("$.mode").value("AUTO"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"reason": "COOLDOWN", "retryAfterSec": 42,
                                 "lastSummaryAt": "2026-07-29T21:03:00Z"}
                                """));

        ChatAiSummaryCall call = gateway.requestSummary(1L, SummaryMode.AUTO);

        ChatAiSummaryCall.Cooldown cooldown = (ChatAiSummaryCall.Cooldown) call;
        assertThat(cooldown.retryAfterSec()).isEqualTo(42);
        assertThat(cooldown.lastSummaryAt()).isEqualTo(Instant.parse("2026-07-29T21:03:00Z"));
    }

    @Test
    void summaryInFlightRejectionBecomesInFlight() {
        server.expect(requestTo("http://chat-ai:8001/broadcasts/1/summary"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"reason\": \"IN_FLIGHT\"}"));

        assertThat(gateway.requestSummary(1L, SummaryMode.MANUAL))
                .isInstanceOf(ChatAiSummaryCall.InFlight.class);
    }

    @Test
    void summaryNotEnoughChatsCarriesCounts() {
        server.expect(requestTo("http://chat-ai:8001/broadcasts/1/summary"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"reason\": \"NOT_ENOUGH_CHATS\", \"chatCount\": 12, \"required\": 30}"));

        ChatAiSummaryCall call = gateway.requestSummary(1L, SummaryMode.AUTO);

        ChatAiSummaryCall.NotEnoughChats notEnough = (ChatAiSummaryCall.NotEnoughChats) call;
        assertThat(notEnough.chatCount()).isEqualTo(12);
        assertThat(notEnough.required()).isEqualTo(30);
    }

    @Test
    void summaryUnknownBroadcastBecomesUnknown() {
        server.expect(requestTo("http://chat-ai:8001/broadcasts/1/summary"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"reason\": \"UNKNOWN_BROADCAST\"}"));

        assertThat(gateway.requestSummary(1L, SummaryMode.AUTO))
                .isInstanceOf(ChatAiSummaryCall.UnknownBroadcast.class);
    }

    @Test
    void summaryServerErrorBecomesFailureInsteadOfThrowing() {
        // 503 LLM_UNCONFIGURED·502 LLM 실패 — 전송 실패와 같은 Failure로 눌러쓴다.
        server.expect(requestTo("http://chat-ai:8001/broadcasts/1/summary"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"reason\": \"LLM_UNCONFIGURED\", \"detail\": \"GMS_KEY가 설정되지 않았습니다\"}"));

        assertThat(gateway.requestSummary(1L, SummaryMode.MANUAL))
                .isInstanceOf(ChatAiSummaryCall.Failure.class);
    }

    @Test
    void getSummaryReturnsCachedSummary() {
        server.expect(requestTo("http://chat-ai:8001/broadcasts/9002/summary"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("""
                        {
                          "summary": "음향 문제를 지적하는 반응이 이어집니다.",
                          "dominant": "분노/불만",
                          "distribution": {"분노/불만": 0.4},
                          "chatCount": 900,
                          "windowSec": 180,
                          "generatedAt": "2026-07-29T21:03:00Z",
                          "nextAvailableAt": "2026-07-29T21:04:00Z"
                        }
                        """, MediaType.APPLICATION_JSON));

        ChatAiSummary summary = gateway.getSummary(9002L).orElseThrow();

        assertThat(summary.summary()).isEqualTo("음향 문제를 지적하는 반응이 이어집니다.");
        assertThat(summary.dominant()).isEqualTo("ANGER");
    }

    @Test
    void getSummaryNotFoundMeansNoSummaryYetAndReturnsEmpty() {
        // 계약: 404 NO_SUMMARY = 아직 생성된 요약이 없음(정상 경로).
        server.expect(requestTo("http://chat-ai:8001/broadcasts/1/summary"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(gateway.getSummary(1L)).isEmpty();
    }

    @Test
    void endBroadcastSwallowsFailure() {
        server.expect(requestTo("http://chat-ai:8001/broadcasts/9002"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        gateway.endBroadcast(9002L); // 예외가 새지 않아야 한다
    }

    private static org.springframework.test.web.client.response.DefaultResponseCreator withStatus(HttpStatus status) {
        return org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(status);
    }
}
