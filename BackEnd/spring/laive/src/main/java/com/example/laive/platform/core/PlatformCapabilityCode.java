package com.example.laive.platform.core;

import java.util.Set;

/** 플랫폼별로 선택적으로 제공할 수 있는 기능 코드다. */
public final class PlatformCapabilityCode {

    public static final String OAUTH = "OAUTH";
    public static final String LIVE_DISCOVERY = "LIVE_DISCOVERY";
    public static final String CHAT_COLLECTION = "CHAT_COLLECTION";
    public static final String VIEWER_METRICS = "VIEWER_METRICS";
    public static final String VIDEO_METADATA = "VIDEO_METADATA";
    // 방송 설정(제목 등) 조회·변경. 세션 단위 실행 기능이 아니므로 SESSION_CAPABILITIES에는 넣지 않는다.
    public static final String BROADCAST_SETTINGS = "BROADCAST_SETTINGS";
    // 방송을 켜 두는 기능(유튜브). 준비 단계에서 한 번 실행하고 끝나므로 이것도
    // SESSION_CAPABILITIES가 아니다 — 그쪽은 세션 내내 "동작 중"으로 표시할 기능들이다.
    public static final String BROADCAST_LAUNCH = "BROADCAST_LAUNCH";
    public static final Set<String> SESSION_CAPABILITIES = Set.of(
            LIVE_DISCOVERY, CHAT_COLLECTION, VIEWER_METRICS, VIDEO_METADATA);

    private PlatformCapabilityCode() {
    }
}
