package com.example.laive.streamer.repository;

import com.example.laive.streamer.domain.StreamerAccount;
import com.example.laive.streamer.domain.StreamerTag;

import java.util.List;
import java.util.Optional;

/**
 * 프로필·태그 영속성 Port다. 구현은 repository.mybatis에 있다.
 */
public interface StreamerProfileRepository {

    Optional<StreamerAccount> findAccount(long streamerId);

    /** 소셜 로그인 제공자 목록. 연결 순서대로 반환한다. */
    List<String> findLoginProviders(long streamerId);

    List<StreamerTag> findTags(long streamerId);

    List<StreamerTag> findTagCatalog();

    /** 카탈로그에 실제로 존재하는 태그 수. 요청 개수와 다르면 잘못된 id가 섞인 것이다. */
    long countTagsByIds(List<Long> tagIds);

    int updateProfile(long streamerId, String nickname, String profileImageUrl, String bio);

    void deleteTags(long streamerId);

    void insertTags(long streamerId, List<Long> tagIds);
}
