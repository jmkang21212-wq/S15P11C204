package com.example.laive.broadcast.session.integration;

import java.util.UUID;

/**
 * 종료 통계가 확정된 리포트를 FastAPI 분석 작업에 전달한다.
 */
@FunctionalInterface
public interface ReportGenerationGateway {

    /**
     * 비동기 분석 요청을 수락하면 true, 연동이 비활성화되어 있으면 false를 반환한다.
     */
    boolean requestReport(long reportId, long sessionId, UUID eventId);
}

