package com.example.laive.media.dto.response;

import com.example.laive.media.domain.ClipJob;

import java.util.List;

public record ClipJobsResponse(List<ClipJobResponse> jobs) {

    public static ClipJobsResponse from(List<ClipJob> jobs) {
        return new ClipJobsResponse(jobs.stream().map(ClipJobResponse::from).toList());
    }
}
