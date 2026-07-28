package com.example.laive.broadcast.session.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * FastAPI 리포트 요청의 인증, 멱등 키와 JSON 계약을 검증한다.
 */
class HttpReportGenerationGatewayTest {

    private static final String REPORT_URI =
            "http://localhost:8000/internal/v1/ai/session-reports";

    private MockRestServiceServer server;
    private HttpReportGenerationGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:8000");
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new HttpReportGenerationGateway(builder.build(), "internal-service-token");
    }

    @Test
    void sendsReportRequestWithServiceTokenAndEventId() {
        UUID eventId = UUID.randomUUID();
        server.expect(requestTo(REPORT_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer internal-service-token"))
                .andExpect(header("X-Request-Id", eventId.toString()))
                .andExpect(jsonPath("$.reportId").value(71))
                .andExpect(jsonPath("$.sessionId").value(9002))
                .andExpect(jsonPath("$.inputObjectKey")
                        .value("sessions/9002/analysis/report-input.json"))
                .andRespond(withStatus(HttpStatus.ACCEPTED)
                        .contentType(MediaType.APPLICATION_JSON));

        boolean requested = gateway.requestReport(71, 9002, eventId);

        server.verify();
        assertThat(requested).isTrue();
    }

    @Test
    void rejectsNonSuccessfulFastApiResponse() {
        server.expect(requestTo(REPORT_URI))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> gateway.requestReport(71, 9002, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("status=502");
        server.verify();
    }
}
