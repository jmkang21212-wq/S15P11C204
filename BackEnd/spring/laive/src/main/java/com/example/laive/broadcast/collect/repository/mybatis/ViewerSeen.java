package com.example.laive.broadcast.collect.repository.mybatis;

import java.time.Instant;

/**
 * viewer_profiles Upsert 입력.
 *
 * <p>배치 안에 같은 해시가 여러 번 나오면 가장 이른 시각과 가장 늦은 시각으로
 * 접는다. 하나로 접으면 안 되는 이유: 처음 보는 시청자는 충돌이 없어 INSERT로
 * 들어가는데, 그때 SQL의 LEAST/GREATEST는 실행되지 않는다. 한 값만 넘기면
 * first_seen_at과 last_seen_at이 둘 다 그 값이 되어, 배치가 시간 범위를 걸칠 때
 * 첫 등장 시각이 실제보다 늦게 기록된다.
 *
 * <p>nickname·userRole은 배치에서 마지막으로 관측된 null 아닌 값으로 접는다 —
 * 닉변을 따라가되, 닉네임 없는 항목(YouTube 일부 등)이 이미 아는 값을 지우지
 * 않게 하기 위해서다. SQL 쪽 COALESCE(EXCLUDED…)와 같은 원칙이다.
 */
public record ViewerSeen(
        String viewerHash,
        Instant firstSeenAt,
        Instant lastSeenAt,
        String nickname,
        String userRole
) {
}
