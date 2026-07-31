package com.example.laive.portfolio.repository;

import com.example.laive.portfolio.domain.FeaturedHighlightSelection;
import com.example.laive.portfolio.domain.FeaturedHighlightView;
import com.example.laive.portfolio.domain.OwnedHighlight;
import com.example.laive.portfolio.domain.PerformanceSnapshot;
import com.example.laive.portfolio.domain.PlatformAccountRow;
import com.example.laive.portfolio.domain.PlatformAccountView;
import com.example.laive.portfolio.domain.Portfolio;
import com.example.laive.portfolio.domain.PortfolioSearchCondition;
import com.example.laive.portfolio.domain.PortfolioSearchRow;
import com.example.laive.portfolio.domain.SessionWindow;
import com.example.laive.portfolio.domain.StreamerBasics;
import com.example.laive.portfolio.domain.TagNameRow;
import com.example.laive.streamer.domain.StreamerTag;

import java.util.List;
import java.util.Optional;

/**
 * 포트폴리오 영속성 Port다. 구현은 repository.mybatis에 있다.
 */
public interface PortfolioRepository {

    /** 생성 자격 게이트 — 종료된 방송 세션이 1개 이상인지. */
    boolean existsEndedSession(long streamerId);

    Optional<Portfolio> findByStreamerId(long streamerId);

    /** 수정 검증과 UPDATE가 한 트랜잭션에서 일관되도록 행을 잠근다. */
    Optional<Portfolio> findByStreamerIdForUpdate(long streamerId);

    /** 공개 행만 찾는다. 미존재와 비공개를 구분하지 않는 것이 명세다 (API 12.4). */
    Optional<Portfolio> findPublicBySlug(String publicSlug);

    /** 새 행을 넣고 발급된 id를 돌려준다. */
    long insert(Portfolio portfolio);

    int update(Portfolio portfolio);

    /** 실적 스냅샷 5컬럼을 방송 데이터로 재계산한다. 행이 없으면 아무 일도 없다. */
    void refreshPerformance(long streamerId);

    /** 전체 포트폴리오의 실적을 재계산한다 (일 배치). 갱신된 행 수를 돌려준다. */
    int refreshAllPerformance();

    PerformanceSnapshot findPerformanceSnapshot(long streamerId);

    /** primaryTimeRange 계산 재료 — 최근 90일 종료 세션 최신 30건. */
    List<SessionWindow> findRecentEndedSessions(long streamerId);

    /** 요청한 id 중 본인 방송에서 나온 하이라이트만 돌아온다. 수가 다르면 침입이다. */
    List<OwnedHighlight> findOwnedHighlights(long streamerId, List<Long> highlightIds);

    void deleteFeaturedHighlights(long portfolioId);

    void insertFeaturedHighlights(long portfolioId, List<FeaturedHighlightSelection> selections);

    List<FeaturedHighlightView> findFeaturedHighlights(long portfolioId);

    Optional<StreamerBasics> findStreamerBasics(long streamerId);

    List<PlatformAccountView> findPlatformAccounts(long streamerId);

    List<StreamerTag> findTags(long streamerId);

    List<PortfolioSearchRow> searchPage(
            PortfolioSearchCondition condition, Long excludeStreamerId, int limit, int offset);

    long countSearch(PortfolioSearchCondition condition, Long excludeStreamerId);

    List<PlatformAccountRow> findPlatformAccountsByStreamerIds(List<Long> streamerIds);

    List<TagNameRow> findTagNamesByStreamerIds(List<Long> streamerIds);
}
