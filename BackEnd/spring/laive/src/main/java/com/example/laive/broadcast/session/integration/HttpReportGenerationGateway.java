package com.example.laive.broadcast.session.integration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * API 명세의 FastAPI 세션 리포트 Endpoint를 호출한다.
 */
public class HttpReportGenerationGateway implements ReportGenerationGateway {

    private final RestClient restClient;
    private final String serviceToken;

    public HttpReportGenerationGateway(RestClient restClient, String serviceToken) {
        this.restClient = restClient;
        this.serviceToken = serviceToken;
    }

    @Override
    public boolean requestReport(long reportId, long sessionId, UUID eventId) {
        HttpStatusCode status = restClient.post()
                .uri("/internal/v1/ai/session-reports")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                .header("X-Request-Id", eventId.toString())
                .body(new ReportRequest(
                        reportId,
                        sessionId,
                        "sessions/" + sessionId + "/analysis/report-input.json"
                ))
                .exchange((request, response) -> response.getStatusCode());
        if (!status.is2xxSuccessful()) {
            throw new IllegalStateException("FastAPI 리포트 요청이 실패했습니다. status=" + status.value());
        }
        return true;
    }

    private record ReportRequest(long reportId, long sessionId, String inputObjectKey) {
    }
}

