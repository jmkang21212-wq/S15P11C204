package com.example.laive.media.domain;

/** {@code recordings.status}의 CHECK 제약과 이름을 공유한다. */
public enum RecordingStatus {
    /** OBS가 녹화 중이다. 아직 잘라낼 파일이 확정되지 않았다. */
    RECORDING,
    /** 녹화가 끝나고 파일 경로가 확정됐다. 클립 작업 대상이 된다. */
    COMPLETED,
    /** 녹화가 비정상 종료됐다. 파일이 있어도 신뢰할 수 없다. */
    FAILED
}
