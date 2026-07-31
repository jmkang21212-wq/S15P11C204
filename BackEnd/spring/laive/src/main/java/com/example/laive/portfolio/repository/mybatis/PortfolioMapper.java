package com.example.laive.portfolio.repository.mybatis;

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
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PortfolioMapper {

    boolean existsEndedSession(@Param("streamerId") long streamerId);

    Optional<Portfolio> findByStreamerId(@Param("streamerId") long streamerId);

    Optional<Portfolio> findByStreamerIdForUpdate(@Param("streamerId") long streamerId);

    Optional<Portfolio> findPublicBySlug(@Param("publicSlug") String publicSlug);

    long insert(@Param("p") Portfolio portfolio);

    int update(@Param("p") Portfolio portfolio);

    void refreshPerformance(@Param("streamerId") long streamerId);

    int refreshAllPerformance();

    PerformanceSnapshot findPerformanceSnapshot(@Param("streamerId") long streamerId);

    List<SessionWindow> findRecentEndedSessions(@Param("streamerId") long streamerId);

    List<OwnedHighlight> findOwnedHighlights(
            @Param("streamerId") long streamerId,
            @Param("highlightIds") List<Long> highlightIds
    );

    void deleteFeaturedHighlights(@Param("portfolioId") long portfolioId);

    void insertFeaturedHighlights(
            @Param("portfolioId") long portfolioId,
            @Param("selections") List<FeaturedHighlightSelection> selections
    );

    List<FeaturedHighlightView> findFeaturedHighlights(@Param("portfolioId") long portfolioId);

    Optional<StreamerBasics> findStreamerBasics(@Param("streamerId") long streamerId);

    List<PlatformAccountView> findPlatformAccounts(@Param("streamerId") long streamerId);

    List<StreamerTag> findTags(@Param("streamerId") long streamerId);

    List<PortfolioSearchRow> searchPage(
            @Param("condition") PortfolioSearchCondition condition,
            @Param("excludeStreamerId") Long excludeStreamerId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long countSearch(
            @Param("condition") PortfolioSearchCondition condition,
            @Param("excludeStreamerId") Long excludeStreamerId
    );

    List<PlatformAccountRow> findPlatformAccountsByStreamerIds(
            @Param("streamerIds") List<Long> streamerIds);

    List<TagNameRow> findTagNamesByStreamerIds(
            @Param("streamerIds") List<Long> streamerIds);
}
