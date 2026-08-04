package com.example.laive.portfolio.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 포트폴리오 공개 범위(명세 12.3, 12.4 + 14)를 실제 보안 필터 체인으로 검증한다.
 *
 * <p>검색·공개 상세는 토큰 없이 열리고(공개 디렉터리 /discover 지원),
 * 내 것(/portfolio/me)과 프로필은 여전히 인증이 필요해야 한다 — permitAll 패턴이
 * 한 세그먼트라 /portfolio/me를 삼키지 않는 것까지 확인한다.
 */
@SpringBootTest(properties = {
        "laive.auth.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "laive.platform.token-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE=",
        "laive.internal.service-token=test-internal-service-token-32bytes!",
        "spring.task.scheduling.enabled=false"
})
@AutoConfigureMockMvc
class PortfolioSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 검색은_토큰_없이_열린다() throws Exception {
        mockMvc.perform(get("/api/v1/portfolios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    /** 401이 아니라 404 — 인증이 아니라 존재 여부가 답이 된다는 뜻이다. */
    @Test
    void 공개_상세는_토큰_없이_접근되고_없으면_404다() throws Exception {
        mockMvc.perform(get("/api/v1/portfolios/no-such-slug"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PORTFOLIO_NOT_FOUND"));
    }

    @Test
    void 내_포트폴리오는_토큰이_필요하다() throws Exception {
        mockMvc.perform(get("/api/v1/portfolio/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 프로필과_태그_카탈로그는_토큰이_필요하다() throws Exception {
        mockMvc.perform(get("/api/v1/streamers/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/tags"))
                .andExpect(status().isUnauthorized());
    }
}
