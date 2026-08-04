package com.example.laive.streamer.repository.mybatis;

import com.example.laive.streamer.domain.StreamerAccount;
import com.example.laive.streamer.domain.StreamerTag;
import com.example.laive.streamer.repository.StreamerProfileRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis Mapper 타입이 Service 계층으로 노출되지 않게 하는 Repository Adapter다.
 */
@Repository
public class MyBatisStreamerProfileRepository implements StreamerProfileRepository {

    private final StreamerProfileMapper mapper;

    public MyBatisStreamerProfileRepository(StreamerProfileMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<StreamerAccount> findAccount(long streamerId) {
        return mapper.findAccount(streamerId);
    }

    @Override
    public List<String> findLoginProviders(long streamerId) {
        return mapper.findLoginProviders(streamerId);
    }

    @Override
    public List<StreamerTag> findTags(long streamerId) {
        return mapper.findTags(streamerId);
    }

    @Override
    public List<StreamerTag> findTagCatalog() {
        return mapper.findTagCatalog();
    }

    @Override
    public long countTagsByIds(List<Long> tagIds) {
        return mapper.countTagsByIds(tagIds);
    }

    @Override
    public int updateProfile(long streamerId, String nickname, String profileImageUrl, String bio) {
        return mapper.updateProfile(streamerId, nickname, profileImageUrl, bio);
    }

    @Override
    public void deleteTags(long streamerId) {
        mapper.deleteTags(streamerId);
    }

    @Override
    public void insertTags(long streamerId, List<Long> tagIds) {
        mapper.insertTags(streamerId, tagIds);
    }
}
