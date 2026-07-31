package com.example.laive.media.storage;

/**
 * 저장된 클립의 키다. DB의 {@code clip_url}·{@code thumbnail_url}에 그대로 들어간다.
 *
 * @param clipKey      예: {@code 8821/4412.mp4}
 * @param thumbnailKey 예: {@code 8821/4412.jpg}
 */
public record StoredClip(String clipKey, String thumbnailKey) {
}
