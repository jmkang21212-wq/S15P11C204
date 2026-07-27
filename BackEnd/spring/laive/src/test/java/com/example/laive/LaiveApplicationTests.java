package com.example.laive;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties =
        "laive.auth.jwt.secret=test-only-secret-key-with-at-least-32-bytes")
class LaiveApplicationTests {

    @Test
    void contextLoads() {
    }

}
