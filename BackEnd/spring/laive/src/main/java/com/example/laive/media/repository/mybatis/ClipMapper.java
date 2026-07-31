package com.example.laive.media.repository.mybatis;

import com.example.laive.media.domain.ClipJobRow;
import com.example.laive.media.domain.ClipTarget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ClipMapper {

    List<ClipJobRow> findPendingJobs(@Param("streamerId") long streamerId);

    Optional<ClipTarget> findTarget(
            @Param("streamerId") long streamerId,
            @Param("highlightId") long highlightId
    );

    int markUploaded(
            @Param("highlightId") long highlightId,
            @Param("clipPath") String clipPath,
            @Param("thumbnailPath") String thumbnailPath
    );

    int recordFailure(
            @Param("highlightId") long highlightId,
            @Param("reason") String reason,
            @Param("maxAttempts") int maxAttempts
    );

    int markSkipped(
            @Param("highlightId") long highlightId,
            @Param("reason") String reason
    );
}
