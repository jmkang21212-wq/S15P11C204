package com.example.laive.broadcast.collect.domain;

/**
 * 배치 저장 결과다(명세 13.3.1 응답).
 *
 * @param acceptedCount  실제로 저장된 건수
 * @param duplicateCount 이미 저장돼 있어 건너뛴 건수
 */
public record BatchIngestResult(int acceptedCount, int duplicateCount) {
}
