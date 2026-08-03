package com.example.laive.platform.api;

import com.example.laive.common.api.ApiResponse;
import com.example.laive.platform.core.PlatformAdapterRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 활성 Adapter를 기준으로 지원 플랫폼 카탈로그를 제공한다. */
@RestController
@RequestMapping("/api/v1/platforms")
public class PlatformCatalogController {

    private final PlatformAdapterRegistry registry;

    public PlatformCatalogController(PlatformAdapterRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PlatformDescriptorResponse>>> list() {
        List<PlatformDescriptorResponse> items = registry.enabledDescriptors().stream()
                .map(PlatformDescriptorResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(items));
    }
}
