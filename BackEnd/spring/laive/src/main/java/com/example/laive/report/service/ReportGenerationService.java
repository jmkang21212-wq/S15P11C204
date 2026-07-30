package com.example.laive.report.service;

import org.springframework.stereotype.Service;

@Service
public class ReportGenerationService {
    private final ReportGenerationTransactionService transactions;

    public ReportGenerationService(ReportGenerationTransactionService transactions) {
        this.transactions = transactions;
    }

    public void start(long sessionId, long reportId) {
        transactions.start(sessionId, reportId);
    }
}
