package com.example.laive.broadcast.collect.repository.mybatis;

/**
 * 수집 대상 세션의 소유자 정보다.
 *
 * <p>viewer_profiles가 플랫폼 계정 단위로 유일하고(V1) 토큰 조회에 스트리머 소유
 * 확인이 필요해, 세션 하나를 다룰 때 두 값이 항상 함께 필요하다.
 */
public record SessionCollectionRef(long streamerId, long platformAccountId) {
}
