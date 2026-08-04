package com.example.laive.streamer.repository.mybatis;

import com.example.laive.streamer.domain.StreamerAccount;
import com.example.laive.streamer.domain.StreamerTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface StreamerProfileMapper {

    Optional<StreamerAccount> findAccount(@Param("streamerId") long streamerId);

    List<String> findLoginProviders(@Param("streamerId") long streamerId);

    List<StreamerTag> findTags(@Param("streamerId") long streamerId);

    List<StreamerTag> findTagCatalog();

    long countTagsByIds(@Param("tagIds") List<Long> tagIds);

    int updateProfile(
            @Param("streamerId") long streamerId,
            @Param("nickname") String nickname,
            @Param("profileImageUrl") String profileImageUrl,
            @Param("bio") String bio
    );

    void deleteTags(@Param("streamerId") long streamerId);

    void insertTags(
            @Param("streamerId") long streamerId,
            @Param("tagIds") List<Long> tagIds
    );
}
