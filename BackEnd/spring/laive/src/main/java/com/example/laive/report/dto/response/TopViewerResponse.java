package com.example.laive.report.dto.response;

import com.example.laive.broadcast.fandom.domain.TopViewer;

/**
 * 이 방송의 열성 시청자 한 명이다 (FR-F-03, 총점 내림차순).
 *
 * <p>필드 구성을 {@code LiveSnapshotResponse.TopViewer}와 <b>의도적으로 똑같이</b>
 * 맞춘다. 프론트가 콘솔(방송 중)과 리포트(방송 후)에서 같은 목록 컴포넌트를 쓰기
 * 때문이다 — 한쪽만 필드를 늘리거나 이름을 바꾸면 같은 목록을 두 번 만들어야 한다.
 *
 * <p>다만 <b>데이터 출처는 다르다.</b> 콘솔은 조회 시점의 {@code chat_messages}
 * 집계이고, 이쪽은 방송 종료 시 확정 저장된 {@code viewer_scores}다. 채팅은 90일
 * 후 삭제되므로 리포트가 재집계에 의존하면 오래된 방송이 조용히 빈 목록이 된다.
 *
 * @param nickname    표시용 닉네임. 수집기가 아직 관측하지 못했으면 null — 그때는 viewerAlias를 쓴다
 * @param viewerAlias 해시에서 파생한 익명 별칭(viewer-a91c). 항상 채워진다
 * @param chatScore   분당 3개 캡을 적용한 채팅 점수. <b>실제 채팅 건수가 아니다</b>
 * @param watchScore  채팅을 친 분(minute)의 수 — 꾸준함 지표
 */
public record TopViewerResponse(
        long viewerId,
        String nickname,
        String viewerAlias,
        int chatScore,
        int watchScore,
        double totalScore
) {
    public static TopViewerResponse from(TopViewer viewer) {
        return new TopViewerResponse(
                viewer.viewerId(),
                viewer.nickname(),
                viewer.alias(),
                viewer.chatScore(),
                viewer.watchScore(),
                viewer.totalScore()
        );
    }
}
