package com.example.laive.platform.youtube;

import com.example.laive.platform.config.PlatformProperties;
import com.example.laive.platform.core.PlatformIdentity;
import com.example.laive.platform.core.PlatformOAuthTokens;
import com.example.laive.platform.exception.PlatformException;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.util.List;

/** Google OAuth 2.0과 YouTube 채널 조회를 담당한다. */
@Component
public class YouTubeOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(YouTubeOAuthClient.class);
    private static final String SCOPE = "https://www.googleapis.com/auth/youtube.readonly";

    private final RestClient restClient;
    private final PlatformProperties.YouTube properties;
    private final Clock clock;

    public YouTubeOAuthClient(
            @Qualifier("youtubeRestClient") RestClient restClient,
            PlatformProperties properties,
            Clock clock
    ) {
        this.restClient = restClient;
        this.properties = properties.youtube();
        this.clock = clock;
    }

    public boolean configured() {
        return properties != null
                && notBlank(properties.clientId())
                && notBlank(properties.clientSecret())
                && notBlank(properties.redirectUri());
    }

    public String buildAuthorizationUri(String state) {
        requireConfigured();
        return UriComponentsBuilder.fromUriString(properties.authorizationUri())
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPE)
                .queryParam("access_type", "offline")
                .queryParam("include_granted_scopes", "true")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    public PlatformOAuthTokens exchange(String code) {
        MultiValueMap<String, String> form = baseForm();
        form.add("code", code);
        form.add("redirect_uri", properties.redirectUri());
        form.add("grant_type", "authorization_code");
        return normalize(token(form), null);
    }

    public PlatformOAuthTokens refresh(String refreshToken) {
        MultiValueMap<String, String> form = baseForm();
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");
        return normalize(token(form), refreshToken);
    }

    public PlatformIdentity fetchIdentity(String accessToken) {
        requireConfigured();
        try {
            ChannelList response = restClient.get()
                    .uri(properties.apiBaseUrl() + "/youtube/v3/channels?part=id,snippet&mine=true")
                    .header("Authorization", "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(ChannelList.class);
            if (response == null || response.items() == null || response.items().isEmpty()) {
                throw PlatformException.connectionFailed();
            }
            Channel channel = response.items().getFirst();
            return new PlatformIdentity(channel.id(), channel.snippet().title());
        } catch (RestClientException | NullPointerException exception) {
            log.warn("YouTube channel lookup failed: {}", exception.getClass().getSimpleName());
            throw PlatformException.connectionFailed();
        }
    }

    public void revoke(String accessToken) {
        requireConfigured();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", accessToken);
        try {
            restClient.post()
                    .uri(properties.revokeUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            log.warn("YouTube token revocation failed: {}", exception.getClass().getSimpleName());
            throw PlatformException.connectionFailed();
        }
    }

    private TokenResponse token(MultiValueMap<String, String> form) {
        requireConfigured();
        try {
            TokenResponse response = restClient.post()
                    .uri(properties.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || !notBlank(response.accessToken())) {
                throw PlatformException.connectionFailed();
            }
            return response;
        } catch (RestClientException exception) {
            log.warn("YouTube OAuth token request failed: {}", exception.getClass().getSimpleName());
            throw PlatformException.connectionFailed();
        }
    }

    private PlatformOAuthTokens normalize(TokenResponse response, String existingRefreshToken) {
        String refreshToken = notBlank(response.refreshToken()) ? response.refreshToken() : existingRefreshToken;
        return new PlatformOAuthTokens(
                response.accessToken(),
                refreshToken,
                clock.instant().plusSeconds(response.expiresIn()),
                null,
                response.scope() == null ? SCOPE : response.scope()
        );
    }

    private MultiValueMap<String, String> baseForm() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        return form;
    }

    private void requireConfigured() {
        if (!configured()) {
            throw PlatformException.connectionFailed();
        }
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("refresh_token") String refreshToken,
            String scope,
            @JsonProperty("token_type") String tokenType
    ) {
    }

    record ChannelList(List<Channel> items) {
    }

    record Channel(String id, ChannelSnippet snippet) {
    }

    record ChannelSnippet(String title) {
    }
}
