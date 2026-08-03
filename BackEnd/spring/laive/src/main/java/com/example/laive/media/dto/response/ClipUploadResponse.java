package com.example.laive.media.dto.response;

import com.example.laive.media.service.ClipUploadService.UploadedClip;

public record ClipUploadResponse(String clipUrl, String thumbnailUrl) {

    public static ClipUploadResponse from(UploadedClip uploaded) {
        return new ClipUploadResponse(uploaded.clipUrl(), uploaded.thumbnailUrl());
    }
}
