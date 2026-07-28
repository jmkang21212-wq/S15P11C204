package com.example.laive.platform.api;

import com.example.laive.auth.service.JwtTokenService;
import com.example.laive.platform.domain.PlatformAccount;
import com.example.laive.platform.exception.PlatformCallbackException;
import com.example.laive.platform.exception.PlatformException;
import com.example.laive.platform.service.PlatformAccountService;
import com.example.laive.platform.service.PlatformAccountService.CompletedLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "laive.auth.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "laive.platform.token-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE="
})
class PlatformAccountApiTest {

    private static final long STREAMER_ID = 7L;
    private static final String RETURN_URI = "http://localhost:3000/oauth/callback";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private PlatformAccountService platformAccountService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void requiresAuthenticationForList() throws Exception {
        mockMvc.perform(get("/api/v1/platform-accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    void returnsLinkedAccountsWithoutTokens() throws Exception {
        when(platformAccountService.list(STREAMER_ID)).thenReturn(List.of(new PlatformAccount(
                15L,
                "CHZZK",
                "a253006fe48c3ecbecd3723c8475c6f1",
                "한별TV",
                "CONNECTED",
                Instant.parse("2026-07-20T09:00:00Z")
        )));

        mockMvc.perform(get("/api/v1/platform-accounts").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].platformAccountId").value(15))
                .andExpect(jsonPath("$.data.items[0].platform").value("CHZZK"))
                .andExpect(jsonPath("$.data.items[0].channelName").value("한별TV"))
                .andExpect(jsonPath("$.data.items[0].connectionStatus").value("CONNECTED"))
                // API 명세서 1.2: 시각은 ISO 8601 UTC 문자열로 직렬화한다.
                .andExpect(jsonPath("$.data.items[0].connectedAt").value("2026-07-20T09:00:00Z"))
                .andExpect(jsonPath("$.data.items[0].accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].refreshToken").doesNotExist());
    }

    @Test
    void returnsAuthorizeUrlInsteadOfRedirect() throws Exception {
        when(platformAccountService.startLink(STREAMER_ID, "CHZZK", RETURN_URI))
                .thenReturn("https://chzzk.naver.com/account-interlock?clientId=id&state=abc");

        mockMvc.perform(get("/api/v1/platform-accounts/CHZZK/authorize")
                        .param("returnUri", RETURN_URI)
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorizeUrl")
                        .value("https://chzzk.naver.com/account-interlock?clientId=id&state=abc"));
    }

    @Test
    void acceptsCallbackWithoutAuthenticationAndRedirectsToReturnUri() throws Exception {
        when(platformAccountService.completeLink("state-value", "auth-code", null))
                .thenReturn(new CompletedLink(RETURN_URI, new PlatformAccount(
                        15L, "CHZZK", "channel-id", "한별TV", "CONNECTED", Instant.parse("2026-07-27T00:00:00Z")
                )));

        mockMvc.perform(get("/api/v1/platform-accounts/chzzk/callback")
                        .param("code", "auth-code")
                        .param("state", "state-value"))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        HttpHeaders.LOCATION,
                        RETURN_URI + "?platform=CHZZK&status=connected"
                ));
    }

    @Test
    void redirectsWithErrorCodeWhenCallbackFails() throws Exception {
        when(platformAccountService.completeLink(anyString(), anyString(), eq(null)))
                .thenThrow(new PlatformCallbackException(RETURN_URI, PlatformException.alreadyLinked()));

        mockMvc.perform(get("/api/v1/platform-accounts/chzzk/callback")
                        .param("code", "auth-code")
                        .param("state", "state-value"))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        HttpHeaders.LOCATION,
                        RETURN_URI + "?error=PLATFORM_ALREADY_LINKED"
                ));
    }

    @Test
    void returnsErrorEnvelopeWhenStateIsUnusable() throws Exception {
        // 복귀 주소를 모르는 단계의 실패는 리다이렉트할 곳이 없다.
        when(platformAccountService.completeLink(anyString(), anyString(), eq(null)))
                .thenThrow(PlatformException.invalidRequest("만료되었거나 이미 사용된 요청입니다."));

        mockMvc.perform(get("/api/v1/platform-accounts/chzzk/callback")
                        .param("code", "auth-code")
                        .param("state", "expired-state"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PLATFORM_INVALID_REQUEST"));
    }

    @Test
    void unlinksOwnAccount() throws Exception {
        mockMvc.perform(delete("/api/v1/platform-accounts/15").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNoContent());

        verify(platformAccountService).unlink(STREAMER_ID, 15L);
    }

    @Test
    void mapsReauthRequiredToConflict() throws Exception {
        doThrow(PlatformException.reauthRequired())
                .when(platformAccountService).unlink(anyLong(), anyLong());

        mockMvc.perform(delete("/api/v1/platform-accounts/15").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PLATFORM_REAUTH_REQUIRED"));
    }

    private String bearer() {
        return "Bearer " + jwtTokenService.issue(STREAMER_ID);
    }
}
