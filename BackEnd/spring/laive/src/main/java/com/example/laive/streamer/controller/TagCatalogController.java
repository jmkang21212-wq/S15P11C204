package com.example.laive.streamer.controller;

import com.example.laive.common.api.ApiResponse;
import com.example.laive.streamer.dto.response.TagResponse;
import com.example.laive.streamer.service.StreamerProfileQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 태그 카탈로그 조회 API다 (명세 6.6 확장 — 원안에 없어 신설).
 *
 * <p>프로필 편집과 포트폴리오 생성 화면이 태그 선택지를 그리려면 카탈로그가 필요한데,
 * 명세에 조회 경로가 없었다. 이게 없으면 클라이언트가 tagIds를 알 방법이 없다.
 */
@RestController
@RequestMapping("/api/v1/tags")
public class TagCatalogController {

    private final StreamerProfileQueryService queryService;

    public TagCatalogController(StreamerProfileQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TagResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(
                queryService.getTagCatalog().stream().map(TagResponse::from).toList()));
    }
}
