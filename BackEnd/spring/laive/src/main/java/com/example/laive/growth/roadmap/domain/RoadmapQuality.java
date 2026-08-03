package com.example.laive.growth.roadmap.domain;

public enum RoadmapQuality {
    INITIAL, ESTABLISHED;

    public static RoadmapQuality fromReportCount(long count) {
        return count >= 3 ? ESTABLISHED : INITIAL;
    }
}
