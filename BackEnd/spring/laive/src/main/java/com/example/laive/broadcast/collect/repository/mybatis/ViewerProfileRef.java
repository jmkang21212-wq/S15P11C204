package com.example.laive.broadcast.collect.repository.mybatis;

/**
 * Upsert가 RETURNING으로 돌려준 시청자 프로필 식별자다.
 */
public record ViewerProfileRef(Long viewerId, String viewerHash) {
}
