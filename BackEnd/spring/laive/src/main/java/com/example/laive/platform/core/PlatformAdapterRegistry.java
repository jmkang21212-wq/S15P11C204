package com.example.laive.platform.core;

import com.example.laive.platform.exception.PlatformException;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 등록된 Adapter를 플랫폼 코드로 안전하게 찾아주는 Registry다. */
@Component
public class PlatformAdapterRegistry {

    private final Map<PlatformCode, PlatformIntegrationAdapter> adapters;

    public PlatformAdapterRegistry(List<PlatformIntegrationAdapter> adapters) {
        Map<PlatformCode, PlatformIntegrationAdapter> indexed = new LinkedHashMap<>();
        for (PlatformIntegrationAdapter adapter : adapters) {
            PlatformIntegrationAdapter duplicate = indexed.putIfAbsent(adapter.platformCode(), adapter);
            if (duplicate != null) {
                throw new IllegalStateException("duplicate platform adapter: " + adapter.platformCode());
            }
        }
        this.adapters = Map.copyOf(indexed);
    }

    public PlatformIntegrationAdapter require(String platform) {
        PlatformCode code;
        try {
            code = PlatformCode.of(platform);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw PlatformException.invalidRequest("유효하지 않은 플랫폼 코드입니다.");
        }
        PlatformIntegrationAdapter adapter = adapters.get(code);
        if (adapter == null || !adapter.descriptor().enabled()) {
            throw PlatformException.invalidRequest("지원하지 않는 플랫폼입니다.");
        }
        return adapter;
    }

    public List<PlatformDescriptor> enabledDescriptors() {
        return adapters.values().stream()
                .map(PlatformIntegrationAdapter::descriptor)
                .filter(PlatformDescriptor::enabled)
                .sorted(Comparator.comparing(d -> d.platformCode().value()))
                .toList();
    }
}
