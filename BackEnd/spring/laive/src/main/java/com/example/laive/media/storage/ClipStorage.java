package com.example.laive.media.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 클립·썸네일 파일의 저장소 Port다.
 *
 * <p>돌려주는 값은 <b>절대 URL이 아니라 키</b>다({@code "8821/4412.mp4"}). 절대 URL을
 * DB에 넣으면 도메인이 바뀔 때마다 전 행을 UPDATE해야 하고, 로컬 개발 DB에
 * {@code localhost:8080}이 박히는 문제도 같이 생긴다. 조회 시점에
 * {@link ClipUrlResolver}가 접두사를 붙인다.
 *
 * <p>S3로 옮길 때 이 키가 그대로 오브젝트 키가 되므로 구현체만 교체하면 된다.
 */
public interface ClipStorage {

    StoredClip store(long sessionId, long highlightId, MultipartFile clip, MultipartFile thumbnail);
}
