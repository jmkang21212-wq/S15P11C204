package com.example.laive.broadcast.analysis.repository.mybatis;

/**
 * 전사 Upsert 결과다. 새로 만들었는지 이미 있던 것인지 구분한다.
 *
 * <p>inserted를 구분해야 배치 응답의 중복 건수를 셀 수 있다. 위험 경고가 먼저
 * 도착해 전사를 만들어두면, 뒤따라 오는 배치에서 같은 발화가 중복으로 잡힌다.
 */
public record TranscriptRow(
        long transcriptId,
        boolean inserted
) {
}
