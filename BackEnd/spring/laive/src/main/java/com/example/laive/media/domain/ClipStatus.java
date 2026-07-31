package com.example.laive.media.domain;

/** {@code highlights.clip_status}의 CHECK 제약과 이름을 공유한다. */
public enum ClipStatus {
    /** 아직 클립이 없다. 앱이 가져갈 대상이다. */
    PENDING,
    /** 클립 저장 완료. clip_url이 채워져 있다. */
    UPLOADED,
    /** 재시도 상한을 소진했다. 더 시도하지 않는다. */
    FAILED,
    /** 녹화본이 없어 만들 수 없다. 재시도해도 결과가 같다. */
    SKIPPED
}
