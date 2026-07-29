package com.example.laive.broadcast.realtime.domain.highlight;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * 최근 N초의 채팅으로 유니크 채터 수를 O(1)로 유지한다(HIGHLIGHT_SPEC §2).
 *
 * <p>uid별 횟수를 함께 세는 이유: 같은 사람의 옛 채팅 하나가 윈도우를 벗어나도
 * 그 사람의 다른 채팅이 남아 있으면 유니크에서 빼면 안 된다.
 *
 * <p>스레드 안전하지 않다. 호출자(세션 상태)가 동기화한다.
 */
public class EngagementWindow {

    private record Entry(Instant ts, String uid) {
    }

    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private final Map<String, Integer> countByUid = new HashMap<>();

    /** 채팅 한 건을 넣는다. ts는 서버 수신 시각이어야 한다(가드 8 — 클라이언트 시계 금지). */
    public void add(Instant ts, String uid) {
        entries.addLast(new Entry(ts, uid));
        countByUid.merge(uid, 1, Integer::sum);
    }

    /** cutoff보다 오래된 채팅을 내보낸다. 매 틱 호출한다. */
    public void evictBefore(Instant cutoff) {
        while (!entries.isEmpty() && entries.peekFirst().ts().isBefore(cutoff)) {
            Entry evicted = entries.removeFirst();
            countByUid.computeIfPresent(evicted.uid(), (uid, count) -> count == 1 ? null : count - 1);
        }
    }

    public int uniqueChatters() {
        return countByUid.size();
    }

    public int chatCount() {
        return entries.size();
    }
}
