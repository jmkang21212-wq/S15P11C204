package com.example.laive.media.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * 저장된 클립을 정적 파일로 서빙한다.
 *
 * <p>배포에서는 nginx가 같은 디렉터리를 먼저 받아 가므로 이 핸들러까지 오지 않는다.
 * 그럼에도 두는 이유는 <b>로컬에서 배포 없이 전 구간을 확인할 수 있어야 하기 때문</b>이다.
 * nginx가 있어야만 재생이 되면 오프셋이 맞는지, 클립이 제대로 잘렸는지를 EC2에
 * 올리기 전에는 볼 수 없다.
 *
 * <p>Spring의 {@code ResourceHttpRequestHandler}는 Range 요청을 지원하므로
 * {@code <video>}의 구간 탐색도 동작한다. 다만 sendfile을 쓰지 못해 큰 파일에서는
 * nginx보다 느리다 — 실사용 트래픽은 nginx에 맡긴다.
 */
@Configuration
public class ClipResourceConfig implements WebMvcConfigurer {

    private final String urlPattern;
    private final String location;

    public ClipResourceConfig(ClipProperties properties) {
        // 서빙 경로는 public-base-url에서 뽑는다. 따로 설정하게 하면 둘이 어긋났을 때
        // "URL은 나오는데 404"라는 진단하기 어려운 증상이 된다.
        this.urlPattern = pathOf(properties.publicBaseUrl()) + "/**";
        this.location = Path.of(properties.storageDir()).toUri().toString();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(urlPattern).addResourceLocations(location);
    }

    /** {@code https://host/clips} → {@code /clips}, 경로가 없으면 {@code /clips}로 둔다. */
    private static String pathOf(String baseUrl) {
        String path = java.net.URI.create(baseUrl).getPath();
        if (path == null || path.isBlank() || path.equals("/")) {
            return "/clips";
        }
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
