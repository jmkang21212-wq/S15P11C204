package com.example.laive.broadcast.analysis.repository.mybatis;

/**
 * 방송 시작 기준 밀리초로 환산이 끝난 전사 한 건이다.
 */
public record TranscriptUpsert(
        long startMs,
        long endMs,
        String transcript
) {
}
