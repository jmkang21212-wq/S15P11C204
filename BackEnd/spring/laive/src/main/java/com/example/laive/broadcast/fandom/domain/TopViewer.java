package com.example.laive.broadcast.fandom.domain;

/**
 * 세션 열성 시청자 한 명의 집계 결과다 (FR-F-03).
 *
 * <p>점수 산식은 도배에 뚫리지 않게 설계됐다 (ViewerScoreMapper.xml의
 * viewerScoreAggregate가 단일 원천):
 * <pre>
 * chat_score  = Σ min(그 분(minute)의 채팅 수, 3)   — 분당 캡
 * watch_score = 채팅 친 분(distinct minute)의 수     — 꾸준함
 * total       = chat + watch×2 + Σ log10(후원액+1)×10
 * </pre>
 *
 * <p>nickname은 아직 관측되지 않았으면 null이다 — 화면은 viewerHash에서 파생한
 * 별칭(viewer-a91c)으로 폴백한다.
 */
public record TopViewer(
        long viewerId,
        String nickname,
        String viewerHash,
        int chatScore,
        int watchScore,
        double totalScore
) {
}
