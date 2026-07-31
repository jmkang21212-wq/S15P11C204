package com.example.laive.portfolio.service;

import com.example.laive.common.api.PageResponse;
import com.example.laive.portfolio.domain.PerformanceSnapshot;
import com.example.laive.portfolio.domain.PlatformAccountRow;
import com.example.laive.portfolio.domain.Portfolio;
import com.example.laive.portfolio.domain.PortfolioDetail;
import com.example.laive.portfolio.domain.PortfolioPerformance;
import com.example.laive.portfolio.domain.PortfolioProfileView;
import com.example.laive.portfolio.domain.PortfolioSearchCondition;
import com.example.laive.portfolio.domain.PortfolioSearchItem;
import com.example.laive.portfolio.domain.PortfolioSearchRow;
import com.example.laive.portfolio.domain.SessionWindow;
import com.example.laive.portfolio.domain.StreamerBasics;
import com.example.laive.portfolio.domain.TagNameRow;
import com.example.laive.portfolio.exception.PortfolioException;
import com.example.laive.portfolio.repository.PortfolioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 포트폴리오 조회·검색을 담당한다 (API 12.1, 12.3, 12.4).
 *
 * <p>응답의 profile·tags는 포트폴리오가 소유한 데이터가 아니라 STREAMERS·
 * PLATFORM_ACCOUNTS·STREAMER_TAGS를 조회 시 조인해 붙인다 — 프로필(6.6)과의
 * "연동"의 실체가 이 조립이다.
 */
@Service
@Transactional(readOnly = true)
public class PortfolioQueryService {

    /** 방송 시간대 표시는 시청자가 보는 한국 시각 기준이다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final PortfolioRepository repository;

    public PortfolioQueryService(PortfolioRepository repository) {
        this.repository = repository;
    }

    /**
     * 내 포트폴리오를 조회한다.
     *
     * <p>미생성이면 404를 두 코드로 나눈다 — 종료된 방송이 없으면 NOT_ELIGIBLE
     * ("첫 방송 전" 화면), 있으면 NOT_CREATED("생성하기 버튼" 화면). 프론트가
     * 이 코드로 화면을 분기한다.
     */
    public PortfolioDetail getMyPortfolio(long streamerId) {
        Portfolio portfolio = repository.findByStreamerId(streamerId)
                .orElseThrow(() -> missingPortfolio(streamerId));
        return assemble(portfolio);
    }

    /** 공개 포트폴리오를 슬러그로 조회한다. 미존재·비공개 모두 같은 404다 (API 12.4). */
    public PortfolioDetail getPublicPortfolio(String publicSlug) {
        Portfolio portfolio = repository.findPublicBySlug(publicSlug)
                .orElseThrow(PortfolioException::notFound);
        return assemble(portfolio);
    }

    /**
     * 공개 포트폴리오를 검색한다 (API 12.3).
     *
     * <p>viewerStreamerId가 null이면 익명 검색이다(공개 디렉터리 /discover) —
     * 본인 제외 없이 전체 공개 행을 대상으로 한다. 로그인 상태면 본인을 뺀다.
     */
    public PageResponse<PortfolioSearchItem> search(
            PortfolioSearchCondition condition, Long viewerStreamerId, int page, int size) {
        long total = repository.countSearch(condition, viewerStreamerId);
        if (total == 0) {
            return PageResponse.of(List.of(), page, size, 0);
        }

        List<PortfolioSearchRow> rows =
                repository.searchPage(condition, viewerStreamerId, size, page * size);
        List<Long> streamerIds = rows.stream().map(PortfolioSearchRow::streamerId).toList();

        Map<Long, List<PlatformAccountRow>> accounts = groupBy(
                repository.findPlatformAccountsByStreamerIds(streamerIds),
                PlatformAccountRow::streamerId);
        Map<Long, List<TagNameRow>> tagNames = groupBy(
                repository.findTagNamesByStreamerIds(streamerIds),
                TagNameRow::streamerId);

        List<PortfolioSearchItem> items = rows.stream()
                .map(row -> new PortfolioSearchItem(
                        row.portfolioId(),
                        row.publicSlug(),
                        row.nickname(),
                        row.profileImageUrl(),
                        accounts.getOrDefault(row.streamerId(), List.of()).stream()
                                .map(PlatformAccountRow::toView)
                                .toList(),
                        tagNames.getOrDefault(row.streamerId(), List.of()).stream()
                                .map(TagNameRow::name)
                                .toList(),
                        row.avgViewers(),
                        row.broadcastsPerWeek()))
                .toList();
        return PageResponse.of(items, page, size, total);
    }

    private PortfolioException missingPortfolio(long streamerId) {
        return repository.existsEndedSession(streamerId)
                ? PortfolioException.notCreated()
                : PortfolioException.notEligible();
    }

    private static <T> Map<Long, List<T>> groupBy(List<T> rows, Function<T, Long> key) {
        return rows.stream().collect(Collectors.groupingBy(key));
    }

    private PortfolioDetail assemble(Portfolio portfolio) {
        long streamerId = portfolio.streamerId();
        StreamerBasics basics = repository.findStreamerBasics(streamerId)
                .orElseThrow(PortfolioException::notFound);
        PerformanceSnapshot snapshot = repository.findPerformanceSnapshot(streamerId);

        return new PortfolioDetail(
                portfolio,
                new PortfolioProfileView(
                        basics.nickname(),
                        basics.profileImageUrl(),
                        repository.findPlatformAccounts(streamerId)),
                repository.findTags(streamerId),
                new PortfolioPerformance(
                        snapshot.avgViewers(),
                        snapshot.peakViewers(),
                        snapshot.broadcastsPerWeek(),
                        snapshot.totalBroadcastCount(),
                        snapshot.totalChat(),
                        primaryTimeRange(repository.findRecentEndedSessions(streamerId))),
                repository.findFeaturedHighlights(portfolio.portfolioId()));
    }

    /**
     * 주 방송 시간대를 계산한다 (API 12.1 — 명세가 못박은 계산 필드).
     *
     * <p>최근 종료 방송들의 시작·종료 "시(hour)" 최빈값을 KST로 뽑아
     * {@code "21:00-01:00"} 형태로 만든다. 원형 평균 같은 정밀한 방법은 과하다 —
     * 표시 목적이고, 자정을 넘는 방송도 최빈값이면 자연스럽게 담긴다.
     * 방송 기록이 없으면 "-"를 준다 (프론트 타입이 non-null string).
     */
    static String primaryTimeRange(List<SessionWindow> sessions) {
        if (sessions.isEmpty()) {
            return "-";
        }
        int startHour = modeHour(sessions, SessionWindow::startedAt);
        int endHour = modeHour(sessions, SessionWindow::endedAt);
        return String.format("%02d:00-%02d:00", startHour, endHour);
    }

    /** 최빈 시각. 동률이면 이른 시각을 골라 결과를 결정적으로 만든다. */
    private static int modeHour(List<SessionWindow> sessions, Function<SessionWindow, Instant> field) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (SessionWindow session : sessions) {
            int hour = field.apply(session).atZone(SERVICE_ZONE).getHour();
            counts.merge(hour, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .max(Map.Entry.<Integer, Integer>comparingByValue()
                        .thenComparing(entry -> -entry.getKey()))
                .orElseThrow()
                .getKey();
    }
}
