package com.example.laive.broadcast.collect.service;

import com.example.laive.broadcast.collect.dto.response.CollectorCredentialsResponse;
import com.example.laive.broadcast.collect.repository.mybatis.CollectMapper;
import com.example.laive.broadcast.collect.repository.mybatis.SessionCollectionRef;
import com.example.laive.broadcast.collect.repository.mybatis.PlatformCollectorSpecRow;
import com.example.laive.broadcast.collect.dto.response.PlatformCollectorSpecResponse;
import com.example.laive.broadcast.session.exception.BroadcastSessionException;
import com.example.laive.platform.domain.PlatformAccountSecret;
import com.example.laive.platform.persistence.PlatformAccountMapper;
import com.example.laive.platform.service.PlatformTokenRefresher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수집기가 치지직 핸드셰이크에 쓸 Access Token과 채널 ID를 발급한다.
 *
 * <p>토큰은 암호화되어 저장되고 복호화 키는 Spring만 갖는다(NFR-S-03). 수집기가
 * DB나 키를 직접 보지 않게 하려면 이 경로가 필요하다.
 */
@Service
public class CollectorCredentialsService {

    private final CollectMapper collectMapper;
    private final PlatformAccountMapper platformAccountMapper;
    private final PlatformTokenRefresher tokenRefresher;

    public CollectorCredentialsService(
            CollectMapper collectMapper,
            PlatformAccountMapper platformAccountMapper,
            PlatformTokenRefresher tokenRefresher
    ) {
        this.collectMapper = collectMapper;
        this.platformAccountMapper = platformAccountMapper;
        this.tokenRefresher = tokenRefresher;
    }

    @Transactional
    public CollectorCredentialsResponse issue(long sessionId) {
        SessionCollectionRef session = collectMapper.findSessionRef(sessionId)
                .orElseThrow(BroadcastSessionException::notFound);

        // 만료가 임박했으면 여기서 갱신된다. 수집기가 핸드셰이크마다 호출하므로
        // 긴 방송 도중 토큰이 만료돼도 다음 재핸드셰이크가 스스로 회복한다.
        PlatformTokenRefresher.TokenResolution resolution =
                tokenRefresher.resolve(session.streamerId(), session.platformAccountId());
        if (resolution.reauthRequired()) {
            throw BroadcastSessionException.platformReauthRequired();
        }

        PlatformAccountSecret secret = platformAccountMapper
                .findSecretByIdAndStreamerId(session.platformAccountId(), session.streamerId())
                .orElseThrow(BroadcastSessionException::platformAccountNotFound);

        return new CollectorCredentialsResponse(resolution.accessToken(), secret.channelId());
    }

    @Transactional
    public PlatformCollectorSpecResponse issuePlatformSession(long platformSessionId) {
        PlatformCollectorSpecRow spec = collectMapper.findPlatformCollectorSpec(platformSessionId)
                .orElseThrow(BroadcastSessionException::notFound);
        PlatformTokenRefresher.TokenResolution resolution = tokenRefresher.resolve(
                spec.streamerId(), spec.platformAccountId());
        if (resolution.reauthRequired()) {
            throw BroadcastSessionException.platformReauthRequired();
        }
        return new PlatformCollectorSpecResponse(
                spec.platformSessionId(),
                spec.sessionId(),
                spec.platformCode(),
                resolution.accessToken(),
                spec.externalAccountId(),
                spec.channelName(),
                spec.externalBroadcastId(),
                spec.liveChatId(),
                spec.watchUrl()
        );
    }
}
