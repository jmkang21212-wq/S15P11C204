package com.example.laive.report.domain;

import com.example.laive.broadcast.realtime.domain.SentimentLabel;

import java.time.Instant;

public record ChatExcerpt(Instant sentAt, String text, SentimentLabel sentimentLabel) {
}
