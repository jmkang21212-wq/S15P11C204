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

    /**
     * 해시에서 파생한 익명 별칭이다 — 명세 11.5의 viewerAlias 형식(viewer-a91c).
     * "sha256:" 프리픽스 뒤 4자를 쓴다.
     *
     * <p>여기 한 곳에 두는 이유는 콘솔 스냅샷과 세션 리포트가 같은 사람을 <b>같은
     * 별칭</b>으로 보여야 하기 때문이다. 두 화면이 각자 파생하면 형식이 갈라져도
     * 아무도 모른다 — 점수 산식을 {@code <sql>} 조각으로 공유하는 것과 같은 이유다.
     *
     * <p>해시 원문은 프론트로 나가지 않는다. 응답에 싣는 것은 이 별칭뿐이다.
     */
    public String alias() {
        int colon = viewerHash.indexOf(':');
        String digest = colon >= 0 ? viewerHash.substring(colon + 1) : viewerHash;
        return "viewer-" + digest.substring(0, Math.min(4, digest.length()));
    }
}
