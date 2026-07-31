package com.example.laive.portfolio.controller;

import com.example.laive.common.api.ApiResponse;
import com.example.laive.common.api.PageResponse;
import com.example.laive.common.security.JwtStreamerIdExtractor;
import com.example.laive.portfolio.domain.PortfolioSearchCondition;
import com.example.laive.portfolio.domain.PortfolioSearchItem;
import com.example.laive.portfolio.domain.PortfolioSort;
import com.example.laive.portfolio.dto.request.CreatePortfolioRequest;
import com.example.laive.portfolio.dto.request.UpdatePortfolioRequest;
import com.example.laive.portfolio.dto.response.PortfolioResponse;
import com.example.laive.portfolio.dto.response.PortfolioSearchItemResponse;
import com.example.laive.portfolio.dto.response.PublicPortfolioResponse;
import com.example.laive.portfolio.service.PortfolioCommandService;
import com.example.laive.portfolio.service.PortfolioPerformanceService;
import com.example.laive.portfolio.service.PortfolioQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 포트폴리오 API다 (명세 12).
 *
 * <p>경로가 명세대로 둘로 나뉜다 — 내 것은 단수 {@code /portfolio/me},
 * 공개 탐색은 복수 {@code /portfolios}. 그래서 클래스 레벨은 {@code /api/v1}이고
 * 메서드가 전체 경로를 갖는다.
 */
@Validated
@RestController
@RequestMapping("/api/v1")
public class PortfolioController {

    private final PortfolioQueryService queryService;
    private final PortfolioCommandService commandService;
    private final PortfolioPerformanceService performanceService;
    private final JwtStreamerIdExtractor streamerIdExtractor;

    public PortfolioController(
            PortfolioQueryService queryService,
            PortfolioCommandService commandService,
            PortfolioPerformanceService performanceService,
            JwtStreamerIdExtractor streamerIdExtractor
    ) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.performanceService = performanceService;
        this.streamerIdExtractor = streamerIdExtractor;
    }

    /**
     * 내 포트폴리오 조회 (API 12.1).
     *
     * <p>실적 스냅샷을 먼저 재계산한다 — 본인이 보는 숫자는 항상 최신이어야 한다.
     * 미생성이면 404를 두 코드로 나눠 프론트가 "첫 방송 전"과 "생성 가능" 화면을
     * 구분하게 한다 (PORTFOLIO_NOT_ELIGIBLE / PORTFOLIO_NOT_CREATED).
     */
    @GetMapping("/portfolio/me")
    public ResponseEntity<ApiResponse<PortfolioResponse>> myPortfolio(
            @AuthenticationPrincipal Jwt jwt
    ) {
        long streamerId = streamerIdExtractor.extract(jwt);
        // 포트폴리오가 없으면 no-op이므로 404 분기보다 먼저 불러도 안전하다.
        performanceService.refresh(streamerId);
        return ResponseEntity.ok(ApiResponse.success(
                PortfolioResponse.from(queryService.getMyPortfolio(streamerId))));
    }

    /**
     * 포트폴리오 생성 (API 12 신설).
     *
     * <p>종료된 방송 세션이 1개 이상 있어야 하고, 태그·대표 하이라이트·공개 여부를
     * 본문에서 직접 정해야 한다(자동 구성 없음). 이미 있으면 409다.
     */
    @PostMapping("/portfolio/me")
    public ResponseEntity<ApiResponse<PortfolioResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreatePortfolioRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                PortfolioResponse.from(commandService.create(
                        streamerIdExtractor.extract(jwt), request.toCommand()))));
    }

    /** 보낸 필드만 수정한다 (API 12.2). 하이라이트 배열은 전달 시 전체 치환이다. */
    @PatchMapping("/portfolio/me")
    public ResponseEntity<ApiResponse<PortfolioResponse>> update(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdatePortfolioRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                PortfolioResponse.from(commandService.update(
                        streamerIdExtractor.extract(jwt), request.toCommand()))));
    }

    /**
     * 공개 포트폴리오 검색 (API 12.3).
     *
     * <p>익명 허용이다 — 공개 디렉터리(/discover)는 로그인 없이 접근하는 화면이라
     * 명세 14의 "로그인한 스트리머"를 완화했다. 토큰이 있으면 본인 것을 뺀다.
     */
    @GetMapping("/portfolios")
    public ResponseEntity<ApiResponse<PageResponse<PortfolioSearchItemResponse>>> search(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) @Min(0) Integer minAvgViewers,
            @RequestParam(required = false) @Min(0) Integer maxAvgViewers,
            @RequestParam(defaultValue = "RECENT") PortfolioSort sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        Long viewerStreamerId = jwt == null ? null : streamerIdExtractor.extract(jwt);
        PageResponse<PortfolioSearchItem> result = queryService.search(
                new PortfolioSearchCondition(keyword, platform, tag, minAvgViewers, maxAvgViewers, sort),
                viewerStreamerId, page, size);

        List<PortfolioSearchItemResponse> items = result.items().stream()
                .map(PortfolioSearchItemResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(new PageResponse<>(
                items, result.page(), result.size(), result.totalElements(), result.totalPages())));
    }

    /**
     * 공개 포트폴리오 상세 (API 12.4). 인증 없이 조회할 수 있다.
     *
     * <p>비공개·미존재 모두 같은 404다 — 비공개 포트폴리오의 존재를 노출하지 않는다.
     */
    @GetMapping("/portfolios/{publicSlug}")
    public ResponseEntity<ApiResponse<PublicPortfolioResponse>> publicPortfolio(
            @PathVariable String publicSlug
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                PublicPortfolioResponse.from(queryService.getPublicPortfolio(publicSlug))));
    }
}
