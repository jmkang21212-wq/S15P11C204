package com.example.laive.broadcast.analysis.dto.response;

/**
 * 위험 발언·약속 저장 응답이다. 저장된 행 ID를 돌려줘 AI 로그와 대조할 수 있게 한다.
 */
public record AnalysisIngestResponse(long id) {
}
