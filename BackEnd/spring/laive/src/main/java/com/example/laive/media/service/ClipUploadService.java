package com.example.laive.media.service;

import com.example.laive.media.config.ClipProperties;
import com.example.laive.media.domain.ClipStatus;
import com.example.laive.media.domain.ClipTarget;
import com.example.laive.media.exception.MediaException;
import com.example.laive.media.repository.ClipRepository;
import com.example.laive.media.storage.ClipStorage;
import com.example.laive.media.storage.ClipUrlResolver;
import com.example.laive.media.storage.StoredClip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 앱이 만들어 올린 클립을 저장하고 하이라이트에 반영한다(CLIP_SPEC.md §6.5~6.6).
 */
@Service
public class ClipUploadService {

    private static final Logger log = LoggerFactory.getLogger(ClipUploadService.class);

    private final ClipRepository repository;
    private final ClipStorage storage;
    private final ClipUrlResolver urlResolver;
    private final ClipProperties properties;

    public ClipUploadService(
            ClipRepository repository,
            ClipStorage storage,
            ClipUrlResolver urlResolver,
            ClipProperties properties
    ) {
        this.repository = repository;
        this.storage = storage;
        this.urlResolver = urlResolver;
        this.properties = properties;
    }

    /**
     * 클립을 저장하고 URL을 돌려준다.
     *
     * <p>이미 업로드된 하이라이트에 다시 오면 409가 아니라 <b>기존 URL로 200</b>이다.
     * 저장은 성공했는데 응답을 못 받고 앱이 꺼지는 것이 정상 흐름이라, 여기서 에러를
     * 내면 앱이 영원히 재시도한다.
     */
    public UploadedClip upload(
            long streamerId,
            long highlightId,
            MultipartFile clip,
            MultipartFile thumbnail
    ) {
        ClipTarget target = repository.findTarget(streamerId, highlightId)
                .orElseThrow(MediaException::highlightNotFound);

        if (target.clipStatus() == ClipStatus.UPLOADED) {
            log.info("이미 업로드된 클립에 재업로드가 왔습니다. 기존 URL로 응답합니다: highlightId={}",
                    highlightId);
            return toUploaded(target.clipUrl(), target.thumbnailUrl());
        }
        if (target.clipStatus() != ClipStatus.PENDING) {
            throw MediaException.notPending();
        }
        validate(clip, MediaType.valueOf("video/mp4"));
        validate(thumbnail, MediaType.IMAGE_JPEG);

        // 파일 쓰기를 트랜잭션 밖에 둔다. 수십 MB 복사가 커넥션을 점유하면
        // HikariCP(최대 10)가 업로드 몇 건에 고갈된다. 반영은 UPDATE 한 문장이라
        // 그 자체로 원자적이고 감쌀 트랜잭션이 없다.
        StoredClip stored = storage.store(target.sessionId(), highlightId, clip, thumbnail);
        if (repository.markUploaded(highlightId, stored.clipKey(), stored.thumbnailKey()) == 0) {
            // 파일을 쓰는 사이 다른 요청이 먼저 반영했다. 같은 내용이므로 성공으로 본다.
            log.info("동시 업로드가 먼저 반영되었습니다: highlightId={}", highlightId);
        }
        return toUploaded(stored);
    }

    /** 앱이 만들지 못했다고 보고한 실패를 누적한다. */
    @Transactional
    public void fail(long streamerId, long highlightId, String reason) {
        ClipTarget target = repository.findTarget(streamerId, highlightId)
                .orElseThrow(MediaException::highlightNotFound);
        if (target.clipStatus() != ClipStatus.PENDING) {
            return;
        }
        repository.recordFailure(highlightId, truncate(reason), properties.maxAttempts());
    }

    /** 녹화본이 사라진 것처럼 재시도해도 결과가 같은 실패를 즉시 확정한다. */
    @Transactional
    public void skip(long streamerId, long highlightId, String reason) {
        ClipTarget target = repository.findTarget(streamerId, highlightId)
                .orElseThrow(MediaException::highlightNotFound);
        if (target.clipStatus() != ClipStatus.PENDING) {
            return;
        }
        repository.markSkipped(highlightId, truncate(reason));
    }

    private UploadedClip toUploaded(StoredClip stored) {
        return toUploaded(stored.clipKey(), stored.thumbnailKey());
    }

    private UploadedClip toUploaded(String clipKey, String thumbnailKey) {
        return new UploadedClip(urlResolver.toUrl(clipKey), urlResolver.toUrl(thumbnailKey));
    }

    private void validate(MultipartFile file, MediaType expected) {
        if (file == null || file.isEmpty()) {
            throw MediaException.invalidFile();
        }
        if (file.getSize() > properties.maxUploadBytes()) {
            throw MediaException.tooLarge();
        }
        String contentType = file.getContentType();
        if (contentType == null || !expected.isCompatibleWith(MediaType.valueOf(contentType))) {
            throw MediaException.invalidFile();
        }
    }

    /** clip_failed_reason은 VARCHAR(200)이다. 넘치면 INSERT가 거부된다. */
    private String truncate(String reason) {
        if (reason == null || reason.isBlank()) {
            return "unknown";
        }
        return reason.length() <= 200 ? reason : reason.substring(0, 200);
    }

    public record UploadedClip(String clipUrl, String thumbnailUrl) {
    }
}
