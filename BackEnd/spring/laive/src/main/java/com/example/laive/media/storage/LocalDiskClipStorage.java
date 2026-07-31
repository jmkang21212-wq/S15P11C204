package com.example.laive.media.storage;

import com.example.laive.media.config.ClipProperties;
import com.example.laive.media.exception.MediaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 클립을 EC2 로컬 디스크에 저장한다. nginx가 같은 디렉터리를 정적으로 서빙한다.
 *
 * <p>오브젝트 스토리지를 두지 않는 이유는 규모다 — 방송 1회에 ~100MB, 100회에 10GB라
 * S3·MinIO 계층이 주는 것이 없다. 필요해지면 {@link ClipStorage} 구현체만 갈아끼운다.
 */
@Component
public class LocalDiskClipStorage implements ClipStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalDiskClipStorage.class);

    private final Path root;

    public LocalDiskClipStorage(ClipProperties properties) {
        this.root = Path.of(properties.storageDir());
    }

    @Override
    public StoredClip store(
            long sessionId,
            long highlightId,
            MultipartFile clip,
            MultipartFile thumbnail
    ) {
        // 키에 사용자 입력이 섞이지 않는다 — 전부 숫자 식별자라 경로 조작이 불가능하다.
        // 업로드된 파일명은 쓰지 않는다.
        String clipKey = sessionId + "/" + highlightId + ".mp4";
        String thumbnailKey = sessionId + "/" + highlightId + ".jpg";
        try {
            Path directory = root.resolve(String.valueOf(sessionId));
            Files.createDirectories(directory);
            write(clip, root.resolve(clipKey));
            write(thumbnail, root.resolve(thumbnailKey));
        } catch (IOException exception) {
            log.error("클립 저장 실패: sessionId={} highlightId={}", sessionId, highlightId, exception);
            throw MediaException.storageFailed(exception);
        }
        return new StoredClip(clipKey, thumbnailKey);
    }

    /**
     * 같은 하이라이트에 다시 업로드가 오면 덮어쓴다. 리포트 재생성으로 하이라이트가
     * 교체되면 새 id가 발급되므로, 덮어쓰기가 일어나는 것은 재업로드뿐이다.
     */
    private void write(MultipartFile file, Path target) throws IOException {
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
