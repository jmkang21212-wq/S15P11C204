package com.example.laive.growth.dto.response;

import com.example.laive.growth.domain.GrowthAction;

public record GrowthActionResponse(long growthActionId, String title, String content) {
    public static GrowthActionResponse from(GrowthAction action) {
        return new GrowthActionResponse(action.id(), action.title(), action.content());
    }
}
