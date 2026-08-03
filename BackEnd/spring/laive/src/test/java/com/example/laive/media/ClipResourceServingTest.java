package com.example.laive.media;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 저장된 클립이 인증 없이 URL로 내려오는지 확인한다.
 *
 * <p>배포에서는 nginx가 이 경로를 먼저 받아 가지만, 로컬에서 배포 없이 재생까지
 * 확인할 수 있어야 오프셋이 맞는지를 EC2에 올리기 전에 볼 수 있다.
 *
 * <p>보안 필터를 태워서 확인하는 이유는 {@code <video>}가 Bearer 헤더를 붙이지 못하기
 * 때문이다. 이 경로가 인증을 요구하면 리포트 화면에서 재생이 되지 않는데, 그 사실은
 * 필터 체인을 통과시켜야만 드러난다.
 */
@SpringBootTest(properties = {
        "laive.auth.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "laive.platform.token-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE=",
        "spring.task.scheduling.enabled=false",
        "laive.clip.storage-dir=build/test-clips",
        "laive.clip.public-base-url=http://localhost:8080/clips"
})
class ClipResourceServingTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        Path directory = Path.of("build/test-clips/8821");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("4412.mp4"), "fake-video");

        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void 저장된_클립이_인증_없이_내려온다() throws Exception {
        mockMvc.perform(get("/clips/8821/4412.mp4"))
                .andExpect(status().isOk())
                .andExpect(content().string("fake-video"));
    }

    @Test
    void 없는_클립은_404다() throws Exception {
        mockMvc.perform(get("/clips/8821/9999.mp4"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 인증이_필요한_API는_여전히_401이다() throws Exception {
        // /clips/**를 열어준 것이 다른 경로까지 뚫지 않았는지 확인한다.
        mockMvc.perform(get("/api/v1/clip-jobs"))
                .andExpect(status().isUnauthorized());
    }
}
