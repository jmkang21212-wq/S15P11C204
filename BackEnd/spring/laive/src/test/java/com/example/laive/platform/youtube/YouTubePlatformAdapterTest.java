package com.example.laive.platform.youtube;

import com.example.laive.platform.core.PlatformCapabilityCode;
import com.example.laive.platform.core.PlatformLiveSettingUpdate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * liveSetting()은 기본 구현이 Optional.empty()라, 오버라이드가 빠지면
 * 제목 동기화가 조용히 비활성화된다. 배선 자체를 회귀 방지선으로 고정한다.
 */
class YouTubePlatformAdapterTest {

    private final YouTubeOAuthClient oauthClient = mock(YouTubeOAuthClient.class);
    private final YouTubeLiveClient liveClient = mock(YouTubeLiveClient.class);
    private final YouTubePlatformAdapter adapter = new YouTubePlatformAdapter(oauthClient, liveClient);

    @Test
    void exposesLiveSettingCapability() {
        assertThat(adapter.liveSetting()).isPresent();
    }

    @Test
    void declaresBroadcastSettingsCapability() {
        assertThat(adapter.descriptor().capabilities())
                .contains(PlatformCapabilityCode.BROADCAST_SETTINGS);
    }

    @Test
    void delegatesTitleUpdateToLiveClient() {
        adapter.updateLiveSetting("access-token", new PlatformLiveSettingUpdate("새 제목"));

        verify(liveClient).updateLiveSetting("access-token", "새 제목");
    }
}
