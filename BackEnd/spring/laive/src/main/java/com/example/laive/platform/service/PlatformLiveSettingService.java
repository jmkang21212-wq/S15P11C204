package com.example.laive.platform.service;

import com.example.laive.platform.core.PlatformAdapterRegistry;
import com.example.laive.platform.core.PlatformCredentialRejectedException;
import com.example.laive.platform.core.PlatformLiveSetting;
import com.example.laive.platform.core.PlatformLiveSettingCapability;
import com.example.laive.platform.core.PlatformLiveSettingUpdate;
import com.example.laive.platform.core.PlatformScopeInsufficientException;
import com.example.laive.platform.domain.PlatformAccountSecret;
import com.example.laive.platform.exception.PlatformException;
import com.example.laive.platform.persistence.PlatformAccountMapper;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * 연동 계정의 플랫폼 방송 설정(제목)을 조회·변경한다.
 *
 * 권한 부족(403)은 토큰이 다른 API에는 여전히 유효하므로 연동 상태를 건드리지 않고
 * 오류 코드만 내려 재연동을 유도한다. 반면 토큰 거부(401)는 토큰이 플랫폼에서
 * 폐기된 상태이므로 REAUTH_REQUIRED로 기록한다.
 */
@Service
public class PlatformLiveSettingService {

    // 치지직 공식 문서에 제목 길이 제한이 명시돼 있지 않아 치지직 스튜디오 UI 관측치를 따른다.
    static final int TITLE_MAX_LENGTH = 100;

    private final PlatformAccountMapper platformAccountMapper;
    private final PlatformAccountService platformAccountService;
    private final PlatformTokenRefresher tokenRefresher;
    private final PlatformAdapterRegistry adapterRegistry;

    public PlatformLiveSettingService(
            PlatformAccountMapper platformAccountMapper,
            PlatformAccountService platformAccountService,
            PlatformTokenRefresher tokenRefresher,
            PlatformAdapterRegistry adapterRegistry
    ) {
        this.platformAccountMapper = platformAccountMapper;
        this.platformAccountService = platformAccountService;
        this.tokenRefresher = tokenRefresher;
        this.adapterRegistry = adapterRegistry;
    }

    public PlatformLiveSetting get(long streamerId, long platformAccountId) {
        PlatformLiveSettingCapability capability = requireCapability(streamerId, platformAccountId);
        String accessToken = platformAccountService.accessToken(streamerId, platformAccountId);
        return callTranslatingErrors(platformAccountId, () -> capability.fetchLiveSetting(accessToken));
    }

    public PlatformLiveSetting update(long streamerId, long platformAccountId, String title) {
        String normalized = normalizeTitle(title);
        PlatformLiveSettingCapability capability = requireCapability(streamerId, platformAccountId);
        String accessToken = platformAccountService.accessToken(streamerId, platformAccountId);
        callTranslatingErrors(platformAccountId, () -> {
            capability.updateLiveSetting(accessToken, new PlatformLiveSettingUpdate(normalized));
            return null;
        });
        // 변경 성공 응답에는 content가 없어 재조회 대신 요청값을 반영해 돌려준다.
        return new PlatformLiveSetting(normalized, null, null);
    }

    /** 소유권을 검증하고 방송 설정 기능을 가진 Adapter를 찾는다. */
    private PlatformLiveSettingCapability requireCapability(long streamerId, long platformAccountId) {
        PlatformAccountSecret secret = platformAccountMapper
                .findSecretByIdAndStreamerId(platformAccountId, streamerId)
                .orElseThrow(PlatformException::notFound);
        return adapterRegistry.require(secret.platform())
                .liveSetting()
                .orElseThrow(() -> PlatformException.invalidRequest("방송 설정을 지원하지 않는 플랫폼입니다."));
    }

    private <T> T callTranslatingErrors(long platformAccountId, Supplier<T> call) {
        try {
            return call.get();
        } catch (PlatformScopeInsufficientException exception) {
            throw PlatformException.scopeInsufficient();
        } catch (PlatformCredentialRejectedException exception) {
            tokenRefresher.markReauthRequired(platformAccountId, "platform rejected access token on live setting");
            throw PlatformException.reauthRequired();
        }
    }

    private String normalizeTitle(String title) {
        String normalized = title == null ? "" : title.trim();
        if (normalized.isEmpty()) {
            throw PlatformException.invalidRequest("방송 제목을 입력해 주세요.");
        }
        if (normalized.length() > TITLE_MAX_LENGTH) {
            throw PlatformException.invalidRequest("방송 제목은 " + TITLE_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return normalized;
    }
}
