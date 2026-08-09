package com.example.laive.broadcast.fandom.repository.mybatis;

import com.example.laive.broadcast.fandom.domain.TopViewer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 열성 시청자 점수(FR-F-03)의 조회·확정 저장이다.
 *
 * <p>산식 SQL은 XML의 {@code viewerScoreAggregate} 조각 하나가 단일 원천이며,
 * 실시간 top 조회와 종료 시 viewer_scores 확정 저장이 같은 조각을 공유한다 —
 * 두 곳의 공식이 갈라지는 사고를 막기 위해서다.
 */
@Mapper
public interface ViewerScoreMapper {

    /**
     * 진행 중 세션의 상위 시청자. 스트리머 본인·매니저는 "시청자"가 아니므로 뺀다.
     *
     * @param excludedNicknames 노출에서 뺄 닉네임(소문자). 보통
     *                          {@code FandomProperties.excludedNicknames()}를 그대로 넘긴다.
     *                          limit은 필터가 걸린 뒤에 적용되므로 제외된 만큼 다음 순위가
     *                          올라와 목록은 항상 채워진다
     */
    List<TopViewer> findTopViewers(
            @Param("sessionId") long sessionId,
            @Param("limit") int limit,
            @Param("excludedNicknames") List<String> excludedNicknames
    );

    /**
     * 세션 종료 시 점수를 viewer_scores에 굳힌다. chat_messages는 90일 보존 후
     * 삭제되므로 이 행이 유일한 영구 기록이다. ON CONFLICT라 아웃박스 재시도에 멱등.
     */
    int persistViewerScores(@Param("sessionId") long sessionId);
}
