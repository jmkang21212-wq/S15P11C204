package com.example.laive.auth.domain;

/**
 * MyBatis가 INSERT 후 생성된 스트리머 ID를 채울 수 있도록 만든 쓰기 모델이다.
 */
public class StreamerInsert {

    private Long id;
    private final String email;
    private final String nickname;
    private final String profileImageUrl;

    public StreamerInsert(String email, String nickname, String profileImageUrl) {
        this.email = email;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }
}
