package com.example.laive.platform.api;

import com.example.laive.platform.domain.PlatformAccount;

import java.util.List;

/**
 * 연동 목록 응답이다. 토큰은 어떤 필드로도 포함하지 않는다.
 */
public record PlatformAccountListResponse(List<PlatformAccount> items) {

    public static PlatformAccountListResponse of(List<PlatformAccount> accounts) {
        return new PlatformAccountListResponse(List.copyOf(accounts));
    }
}
