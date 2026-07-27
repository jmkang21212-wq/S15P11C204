package com.example.laive.auth.oauth;

import com.example.laive.auth.config.AuthProperties;
import com.example.laive.auth.exception.AuthException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 네이버 OAuth 서버와 통신해 인증 URL 생성, code 교환, 사용자 프로필 조회를 수행한다.
 */
@Component
public class NaverOAuthClient {

    private final RestClient restClient;
    private final AuthProperties.Naver properties;

    public NaverOAuthClient(RestClient.Builder restClientBuilder, AuthProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties.naver();
    }

    /**
     * CSRF 방지용 state와 등록된 Callback URL을 포함한 네이버 인증 URL을 만든다.
     */
    public String buildAuthorizationUri(String state) {
        requireConfigured();
        // 네이버는 redirect_uri를 포함한 각 파라미터 값을 URL 인코딩한 요청을 요구한다.
        return properties.authorizationUri()
                + "?response_type=code"
                + "&client_id=" + encode(properties.clientId())
                + "&redirect_uri=" + encode(properties.redirectUri())
                + "&state=" + encode(state);
    }

    /**
     * authorization code를 네이버 Access Token으로 교환한 뒤 사용자 프로필을 조회한다.
     * 네이버 Access Token은 이 메서드 안에서만 사용하며 DB나 로그에 남기지 않는다.
     */
    public NaverProfile fetchProfile(String code, String state) {
        requireConfigured();
        try {
            String naverAccessToken = exchangeAccessToken(code, state);
            Map<?, ?> userInfo = restClient.get()
                    .uri(properties.userInfoUri())
                    .headers(headers -> headers.setBearerAuth(naverAccessToken))
                    .retrieve()
                    .body(Map.class);

            if (userInfo == null
                    || !"00".equals(stringValue(userInfo.get("resultcode")))
                    || !(userInfo.get("response") instanceof Map<?, ?> response)
                    || stringValue(response.get("id")) == null) {
                throw AuthException.platformConnectionFailed();
            }
            return new NaverProfile(
                    stringValue(response.get("id")),
                    stringValue(response.get("email")),
                    stringValue(response.get("nickname")),
                    stringValue(response.get("name")),
                    stringValue(response.get("profile_image"))
            );
        } catch (AuthException exception) {
            throw exception;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw AuthException.platformConnectionFailed();
        }
    }

    private String exchangeAccessToken(String code, String state) {
        // Client Secret이 URL이나 애플리케이션 로그에 노출되지 않도록 form body로 전송한다.
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("code", code);
        form.add("state", state);

        Map<?, ?> token = restClient.post()
                .uri(properties.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);

        String accessToken = token == null ? null : stringValue(token.get("access_token"));
        if (accessToken == null) {
            throw AuthException.platformConnectionFailed();
        }
        return accessToken;
    }

    private void requireConfigured() {
        // 설정 누락을 네이버 호출 전에 감지해 일관된 외부 플랫폼 오류로 응답한다.
        if (isBlank(properties.clientId())
                || isBlank(properties.clientSecret())
                || isBlank(properties.redirectUri())) {
            throw AuthException.platformConnectionFailed();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String stringValue(Object value) {
        if (!(value instanceof String string) || string.isBlank()) {
            return null;
        }
        return string;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
