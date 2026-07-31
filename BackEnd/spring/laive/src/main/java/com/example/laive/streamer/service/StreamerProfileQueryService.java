package com.example.laive.streamer.service;

import com.example.laive.streamer.domain.StreamerAccount;
import com.example.laive.streamer.domain.StreamerProfile;
import com.example.laive.streamer.domain.StreamerTag;
import com.example.laive.streamer.exception.StreamerProfileException;
import com.example.laive.streamer.repository.StreamerProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 내 프로필과 태그 카탈로그 조회를 담당한다 (API 6.6).
 */
@Service
@Transactional(readOnly = true)
public class StreamerProfileQueryService {

    private final StreamerProfileRepository repository;

    public StreamerProfileQueryService(StreamerProfileRepository repository) {
        this.repository = repository;
    }

    public StreamerProfile getProfile(long streamerId) {
        StreamerAccount account = repository.findAccount(streamerId)
                .orElseThrow(StreamerProfileException::notFound);
        return new StreamerProfile(
                account.streamerId(),
                account.email(),
                account.hasPassword(),
                loginMethods(account),
                account.nickname(),
                account.profileImageUrl(),
                account.bio(),
                repository.findTags(streamerId)
        );
    }

    /** 프로필 편집·포트폴리오 생성 화면이 선택지로 그릴 전체 카탈로그다. */
    public List<StreamerTag> getTagCatalog() {
        return repository.findTagCatalog();
    }

    /** 이메일 로그인 가능 여부(EMAIL) 뒤에 소셜 제공자를 연결 순서대로 붙인다. */
    private List<String> loginMethods(StreamerAccount account) {
        List<String> methods = new ArrayList<>();
        if (account.hasPassword()) {
            methods.add("EMAIL");
        }
        methods.addAll(repository.findLoginProviders(account.streamerId()));
        return methods;
    }
}
