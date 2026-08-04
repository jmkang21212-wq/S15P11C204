package com.example.laive.report.service;

import com.example.laive.report.domain.HighlightCandidate;
import com.example.laive.report.domain.HighlightGenerationContext;

import java.util.List;

public interface HighlightGenerator {
    List<HighlightCandidate> generate(HighlightGenerationContext context);
}
