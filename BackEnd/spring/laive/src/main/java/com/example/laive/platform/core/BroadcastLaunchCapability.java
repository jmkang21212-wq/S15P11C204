package com.example.laive.platform.core;

/**
 * 플랫폼에 방송을 켜 두는 기능이다. {@link LiveDiscoveryCapability}가 "이미 켜진 방송을
 * 찾는" 것이라면, 이쪽은 "찾을 수 있는 상태로 만드는" 앞단이다.
 *
 * <p><b>왜 필요한가</b> — 치지직은 OBS가 송출을 시작하면 알아서 라이브로 전환되지만,
 * 유튜브는 그렇지 않다. 송출 신호만으로는 방송(liveBroadcast)이 생기지 않아서, 사용자가
 * 유튜브 스튜디오나 OBS의 계정 연동 화면에서 방송을 따로 만들어야 했다. "LAIVE에서
 * 제목 넣고 시작 버튼을 누르면 켜진다"는 흐름과 어긋나는 지점이라 이 기능으로 메운다.
 *
 * <p>이 Capability가 없는 플랫폼(치지직)은 지금까지처럼 discovery만 탄다.
 */
public interface BroadcastLaunchCapability {

    /**
     * 송출이 들어오면 라이브가 되도록 방송을 준비한다.
     *
     * <p><b>반드시 멱등해야 한다.</b> Outbox가 최대 8회 재시도하는데, 호출마다 방송을
     * 새로 만들면 채널에 빈 방송이 쌓인다. 이미 라이브이거나 준비된 방송이 있으면
     * 아무것도 하지 않고 돌아와야 한다.
     *
     * @param title 세션 제목. 방송 제목으로 그대로 쓴다
     */
    void ensureLaunched(String accessToken, String title);
}
