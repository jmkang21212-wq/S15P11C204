package com.example.laive.broadcast.fandom.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 열성 시청자(FR-F-03) 노출 설정이다.
 *
 * @param excludedNicknames 열성 시청자 집계에서 뺄 닉네임. 개발·시연 중 팀원 계정이
 *                          TOP5를 차지하는 것을 막는 용도다.
 *                          <p>{@code user_role}로 거르지 않는 이유: 수집기가 매 채팅마다
 *                          플랫폼이 준 역할({@code common_user})을 실어 보내고
 *                          CollectMapper의 upsert가
 *                          {@code COALESCE(EXCLUDED.user_role, ...)}로 덮어쓴다 —
 *                          viewer_profiles를 손으로 'manager'로 바꿔도 그 사람이 채팅을
 *                          한 번 치는 순간 원래대로 돌아간다.
 *                          <p>저장(persistViewerScores)은 손대지 않는다. 기록은 완전하게
 *                          남기고 노출만 거르는 기존 설계(ViewerScoreMapper.xml)를 따른다 —
 *                          목록에서 빼려고 점수 원본을 지우면 되돌릴 수 없다.
 */
@ConfigurationProperties(prefix = "laive.fandom")
public record FandomProperties(List<String> excludedNicknames) {

    /**
     * 닉네임을 소문자로 접어 두고 빈 값을 버린다. 환경변수 미설정 시 빈 문자열 하나가
     * 들어오는데, 그대로 두면 SQL의 {@code COALESCE(nickname, '')}와 맞물려 닉네임이
     * 아직 없는 시청자가 통째로 사라진다. 매칭은 SQL의 {@code lower()}와 짝이다.
     */
    public FandomProperties {
        excludedNicknames = excludedNicknames == null ? List.of() : excludedNicknames.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(nickname -> !nickname.isEmpty())
                .map(nickname -> nickname.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }
}
