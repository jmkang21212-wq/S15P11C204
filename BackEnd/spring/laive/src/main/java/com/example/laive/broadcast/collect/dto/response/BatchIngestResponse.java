package com.example.laive.broadcast.collect.dto.response;

import com.example.laive.broadcast.collect.domain.BatchIngestResult;

/**
 * 배치 저장 응답이다(명세 13.3.1).
 */
public record BatchIngestResponse(int acceptedCount, int duplicateCount) {

    public static BatchIngestResponse from(BatchIngestResult result) {
        return new BatchIngestResponse(result.acceptedCount(), result.duplicateCount());
    }
}
