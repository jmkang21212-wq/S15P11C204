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
import com.example.laive.portfolio.repository.PortfolioRepository;
import com.example.laive.streamer.domain.StreamerTag;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis Mapper 타입이 Service 계층으로 노출되지 않게 하는 Repository Adapter다.
 */
@Repository
public class MyBatisPortfolioRepository implements PortfolioRepository {

    private final PortfolioMapper mapper;

    public MyBatisPortfolioRepository(PortfolioMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean existsEndedSession(long streamerId) {
        return mapper.existsEndedSession(streamerId);
    }

    @Override
    public Optional<Portfolio> findByStreamerId(long streamerId) {
        return mapper.findByStreamerId(streamerId);
    }

    @Override
    public Optional<Portfolio> findByStreamerIdForUpdate(long streamerId) {
        return mapper.findByStreamerIdForUpdate(streamerId);
    }

    @Override
    public Optional<Portfolio> findPublicBySlug(String publicSlug) {
        return mapper.findPublicBySlug(publicSlug);
    }

    @Override
    public long insert(Portfolio portfolio) {
        return mapper.insert(portfolio);
    }

    @Override
    public int update(Portfolio portfolio) {
        return mapper.update(portfolio);
    }

    @Override
    public void refreshPerformance(long streamerId) {
        mapper.refreshPerformance(streamerId);
    }

    @Override
    public int refreshAllPerformance() {
        return mapper.refreshAllPerformance();
    }

    @Override
    public PerformanceSnapshot findPerformanceSnapshot(long streamerId) {
        return mapper.findPerformanceSnapshot(streamerId);
    }

    @Override
    public List<SessionWindow> findRecentEndedSessions(long streamerId) {
        return mapper.findRecentEndedSessions(streamerId);
    }

    @Override
    public List<OwnedHighlight> findOwnedHighlights(long streamerId, List<Long> highlightIds) {
        return mapper.findOwnedHighlights(streamerId, highlightIds);
    }

    @Override
    public void deleteFeaturedHighlights(long portfolioId) {
        mapper.deleteFeaturedHighlights(portfolioId);
    }

    @Override
    public void insertFeaturedHighlights(long portfolioId, List<FeaturedHighlightSelection> selections) {
        mapper.insertFeaturedHighlights(portfolioId, selections);
    }

    @Override
    public List<FeaturedHighlightView> findFeaturedHighlights(long portfolioId) {
        return mapper.findFeaturedHighlights(portfolioId);
    }

    @Override
    public Optional<StreamerBasics> findStreamerBasics(long streamerId) {
        return mapper.findStreamerBasics(streamerId);
    }

    @Override
    public List<PlatformAccountView> findPlatformAccounts(long streamerId) {
        return mapper.findPlatformAccounts(streamerId);
    }

    @Override
    public List<StreamerTag> findTags(long streamerId) {
        return mapper.findTags(streamerId);
    }

    @Override
    public List<PortfolioSearchRow> searchPage(
            PortfolioSearchCondition condition, Long excludeStreamerId, int limit, int offset) {
        return mapper.searchPage(condition, excludeStreamerId, limit, offset);
    }

    @Override
    public long countSearch(PortfolioSearchCondition condition, Long excludeStreamerId) {
        return mapper.countSearch(condition, excludeStreamerId);
    }

    @Override
    public List<PlatformAccountRow> findPlatformAccountsByStreamerIds(List<Long> streamerIds) {
        return mapper.findPlatformAccountsByStreamerIds(streamerIds);
    }

    @Override
    public List<TagNameRow> findTagNamesByStreamerIds(List<Long> streamerIds) {
        return mapper.findTagNamesByStreamerIds(streamerIds);
    }
}
