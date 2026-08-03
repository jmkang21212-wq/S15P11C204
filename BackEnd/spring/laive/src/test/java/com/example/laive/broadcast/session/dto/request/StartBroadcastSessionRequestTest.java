package com.example.laive.broadcast.session.dto.request;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StartBroadcastSessionRequestTest {

    @Test
    void keepsLegacySingleAccountContract() {
        var request = new StartBroadcastSessionRequest(
                11L, null, null, "방송", "게임", "ko");

        assertThat(request.isPlatformSelectionValid()).isTrue();
        assertThat(request.toCommand().platformAccountIds()).containsExactly(11L);
    }

    @Test
    void acceptsMultipleDistinctAccounts() {
        var request = new StartBroadcastSessionRequest(
                null, List.of(11L, 22L), null, "동시 송출", "게임", "ko");

        assertThat(request.isPlatformSelectionValid()).isTrue();
        assertThat(request.isPlatformSelectionDistinct()).isTrue();
        assertThat(request.toCommand().platformAccountIds()).containsExactly(11L, 22L);
    }

    @Test
    void rejectsAmbiguousOrDuplicateSelection() {
        var both = new StartBroadcastSessionRequest(
                11L, List.of(22L), null, "방송", null, "ko");
        var duplicate = new StartBroadcastSessionRequest(
                null, List.of(11L, 11L), null, "방송", null, "ko");

        assertThat(both.isPlatformSelectionValid()).isFalse();
        assertThat(duplicate.isPlatformSelectionDistinct()).isFalse();
    }
}
