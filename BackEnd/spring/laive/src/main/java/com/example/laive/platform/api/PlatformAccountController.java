package com.example.laive.platform.api;

import com.example.laive.auth.api.ApiResponse;
import com.example.laive.platform.service.PlatformAccountService;
import com.example.laive.platform.service.PlatformAccountService.CompletedLink;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * 방송 플랫폼 연동 목록·시작·콜백·해제 API를 제공한다.
 */
@RestController
@RequestMapping("/api/v1/platform-accounts")
public class PlatformAccountController {

    private final PlatformAccountService platformAccountService;

    public PlatformAccountController(PlatformAccountService platformAccountService) {
        this.platformAccountService = platformAccountService;
    }

    /** 내 연동 목록을 조회한다. */
    @GetMapping
    public ResponseEntity<ApiResponse<PlatformAccountListResponse>> list(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                PlatformAccountListResponse.of(platformAccountService.list(streamerId(jwt)))
        ));
    }

    /**
     * 인증된 회원과 복귀 주소를 state에 묶고 플랫폼 인증 URL을 돌려준다.
     * 클라이언트는 이 URL을 외부 브라우저로 연다.
     */
    @GetMapping("/{platform}/authorize")
    public ResponseEntity<ApiResponse<PlatformAuthorizeResponse>> authorize(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String platform,
            @RequestParam String returnUri
    ) {
        String authorizeUrl = platformAccountService.startLink(streamerId(jwt), platform, returnUri);
        return ResponseEntity.ok(ApiResponse.success(new PlatformAuthorizeResponse(authorizeUrl)));
    }

    /**
     * 플랫폼이 전달한 code를 저장하고 클라이언트 복귀 주소로 되돌려 보낸다.
     *
     * 이 엔드포인트는 브라우저 리다이렉트로 들어오므로 인증 헤더가 없다.
     * 연동 주체는 요청 파라미터가 아니라 state에 묶인 값으로만 결정한다.
     */
    @GetMapping("/{platform}/callback")
    public ResponseEntity<Void> callback(
            @PathVariable String platform,
            @RequestParam(required = false) String code,
            @RequestParam String state,
            @RequestParam(required = false) String error
    ) {
        CompletedLink completed = platformAccountService.completeLink(state, code, error);
        URI location = UriComponentsBuilder.fromUriString(completed.returnUri())
                .queryParam("platform", completed.account().platform())
                .queryParam("status", "connected")
                .build()
                .toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    /** 연동을 해제하고 플랫폼 쪽 승인도 정리한다. */
    @DeleteMapping("/{platformAccountId}")
    public ResponseEntity<Void> unlink(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long platformAccountId
    ) {
        platformAccountService.unlink(streamerId(jwt), platformAccountId);
        return ResponseEntity.noContent().build();
    }

    private long streamerId(Jwt jwt) {
        // subject에는 JwtTokenService가 넣은 스트리머 ID가 들어 있다.
        return Long.parseLong(jwt.getSubject());
    }
}
