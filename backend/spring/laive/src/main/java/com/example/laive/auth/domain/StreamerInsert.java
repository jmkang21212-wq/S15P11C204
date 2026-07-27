package com.example.laive.auth.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * MyBatis가 INSERT 후 생성된 스트리머 ID를 채울 수 있도록 만든 쓰기 모델이다.
 */
@Getter
public class StreamerInsert {

    @Setter
    private Long id;
    private final String email;
    private final String nickname;
    private final String profileImageUrl;

    public StreamerInsert(String email, String nickname, String profileImageUrl) {
        this.email = email;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

}
