package com.example.laive.report.ai;

import com.example.laive.report.config.ReportAiProperties;
import com.example.laive.report.domain.ChatExcerpt;
import com.example.laive.report.domain.RiskExcerpt;
import com.example.laive.report.domain.TranscriptExcerpt;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ReportInputCompressor {
    private final ReportAiProperties properties;

    public ReportInputCompressor(ReportAiProperties properties) {
        this.properties = properties;
    }

    public String transcripts(List<TranscriptExcerpt> values) {
        String result = values.stream()
                .map(value -> "[%d-%dms] %s".formatted(value.startMs(), value.endMs(), value.text()))
                .collect(Collectors.joining("\n"));
        return limit(result);
    }

    public String chats(List<ChatExcerpt> values) {
        Map<String, Long> frequencies = values.stream()
                .map(ChatExcerpt::text)
                .map(this::normalize)
                .filter(value -> !value.isBlank())
                .collect(Collectors.groupingBy(
                        value -> value, LinkedHashMap::new, Collectors.counting()
                ));
        String result = frequencies.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(100)
                .map(entry -> "\"%s\" × %d".formatted(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining("\n"));
        return limit(result);
    }

    public String risks(List<RiskExcerpt> values) {
        return limit(values.stream()
                .map(value -> "[%s] %s/%s %s".formatted(
                        value.detectedAt(), value.level(), value.category(),
                        value.advice() == null ? "" : value.advice()
                ))
                .collect(Collectors.joining("\n")));
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ");
    }

    private String limit(String value) {
        int maximum = Math.max(1000, properties.maxInputChars() / 3);
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
