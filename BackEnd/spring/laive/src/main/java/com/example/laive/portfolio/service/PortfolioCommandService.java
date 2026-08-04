package com.example.laive.portfolio.service;

import com.example.laive.portfolio.domain.CreatePortfolioCommand;
import com.example.laive.portfolio.domain.FeaturedHighlightSelection;
import com.example.laive.portfolio.domain.Portfolio;
import com.example.laive.portfolio.domain.PortfolioDetail;
import com.example.laive.portfolio.domain.UpdatePortfolioCommand;
import com.example.laive.portfolio.exception.PortfolioException;
import com.example.laive.portfolio.repository.PortfolioRepository;
import com.example.laive.streamer.service.StreamerTagService;
import com.example.laive.growth.roadmap.service.GrowthRoadmapTransactionService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;

/**
 * 포트폴리오 생성·수정을 담당한다 (API 12 신설 POST, 12.2 PATCH).
 *
 * <p>생성은 자동 구성이 아니다 — 종료된 방송이 1회 이상 있어야 하고(게이트),
 * 태그·대표 하이라이트·공개 여부를 사용자가 직접 정해야 만들어진다.
 * 규칙은 전부 백엔드가 강제한다. 프론트에서만 막으면 API 직접 호출로 뚫린다.
 */
@Service
@Transactional
public class PortfolioCommandService {

    private final PortfolioRepository repository;
    private final StreamerTagService tagService;
    private final PortfolioPerformanceService performanceService;
    private final PortfolioQueryService queryService;
    private final Clock clock;
    private final GrowthRoadmapTransactionService growthRoadmaps;
    /** 슬러그는 공개 주소다 — 추측 불가능해야 하므로 SecureRandom을 쓴다. */
    private final SecureRandom random = new SecureRandom();

    public PortfolioCommandService(
            PortfolioRepository repository,
            StreamerTagService tagService,
            PortfolioPerformanceService performanceService,
            PortfolioQueryService queryService,
            Clock clock,
            GrowthRoadmapTransactionService growthRoadmaps
    ) {
        this.repository = repository;
        this.tagService = tagService;
        this.performanceService = performanceService;
        this.queryService = queryService;
        this.clock = clock;
        this.growthRoadmaps = growthRoadmaps;
    }

    /**
     * 포트폴리오를 생성한다.
     *
     * <p>태그는 프로필(6.6)과 같은 {@link StreamerTagService} 교체 로직을 그대로
     * 호출한다 — 저장 원천이 한 곳(streamer_tags)이라 두 화면의 태그가 갈라질 수 없다.
     * 실적은 생성 즉시 집계해 첫 응답부터 0이 아닌 실제 값이 나가게 한다.
     */
    public PortfolioDetail create(long streamerId, CreatePortfolioCommand command) {
        if (!repository.existsEndedSession(streamerId)) {
            throw PortfolioException.notEligible();
        }
        if (repository.findByStreamerId(streamerId).isPresent()) {
            throw PortfolioException.alreadyExists();
        }

        FeaturedHighlightSelection.requireNoDuplicates(command.featuredHighlights());

        long portfolioId = insertWithRandomSlug(streamerId, command);

        tagService.replaceTags(streamerId, command.tagIds());
        replaceHighlights(streamerId, portfolioId, command.featuredHighlights());
        performanceService.refresh(streamerId);
        growthRoadmaps.requestForPortfolio(streamerId);
        return queryService.getMyPortfolio(streamerId);
    }

    /** 보낸 필드만 수정하고, 수정된 전체 포트폴리오를 돌려준다 (API 12.2). */
    public PortfolioDetail update(long streamerId, UpdatePortfolioCommand command) {
        Portfolio current = repository.findByStreamerIdForUpdate(streamerId)
                .orElseThrow(() -> missingPortfolio(streamerId));

        Portfolio next = current.applyPatch(command, clock.instant());
        repository.update(next);

        if (command.featuredHighlights() != null) {
            // 명시적 null도 빈 배열과 같이 "전부 해제"다.
            List<FeaturedHighlightSelection> selections =
                    command.featuredHighlights().value() == null
                            ? List.of()
                            : command.featuredHighlights().value();
            FeaturedHighlightSelection.requireNoDuplicates(selections);
            replaceHighlights(streamerId, current.portfolioId(), selections);
        }
        if (command.title() != null
                || command.introduction() != null
                || command.featuredHighlights() != null) {
            growthRoadmaps.requestForPortfolio(streamerId);
        }
        return queryService.getMyPortfolio(streamerId);
    }

    /**
     * 무작위 슬러그로 행을 넣는다. UNIQUE 위반이 나면 원인을 가른다 —
     * streamer_id 중복이면 동시 생성 경합이므로 409, 아니면 슬러그 충돌
     * (36^10분의 1)이므로 새 슬러그로 재시도한다.
     */
    private long insertWithRandomSlug(long streamerId, CreatePortfolioCommand command) {
        for (int attempt = 0; ; attempt++) {
            Portfolio portfolio = Portfolio.create(
                    streamerId, Portfolio.randomSlug(random), command, clock.instant());
            try {
                return repository.insert(portfolio);
            } catch (DuplicateKeyException race) {
                if (repository.findByStreamerId(streamerId).isPresent()) {
                    // 동시에 두 번 눌러도 UNIQUE(streamer_id)가 한 건만 통과시킨다.
                    throw PortfolioException.alreadyExists();
                }
                if (attempt >= 2) {
                    throw race;                        // 3연속 슬러그 충돌은 사실상 불가능
                }
            }
        }
    }

    /**
     * 대표 하이라이트를 통째로 교체한다.
     *
     * <p>소유권(내 방송에서 나온 하이라이트인지)을 먼저 확인한다. 순서 재배치가
     * 한 트랜잭션에서 안전한 것은 DEFERRABLE UNIQUE가 커밋 시점에만 검사하기 때문이다.
     */
    private void replaceHighlights(
            long streamerId, long portfolioId, List<FeaturedHighlightSelection> selections) {
        if (!selections.isEmpty()) {
            List<Long> ids = selections.stream()
                    .map(FeaturedHighlightSelection::highlightId)
                    .toList();
            if (repository.findOwnedHighlights(streamerId, ids).size() != ids.size()) {
                throw PortfolioException.highlightInvalid();
            }
        }
        repository.deleteFeaturedHighlights(portfolioId);
        if (!selections.isEmpty()) {
            repository.insertFeaturedHighlights(portfolioId, selections);
        }
    }

    private PortfolioException missingPortfolio(long streamerId) {
        return repository.existsEndedSession(streamerId)
                ? PortfolioException.notCreated()
                : PortfolioException.notEligible();
    }
}
