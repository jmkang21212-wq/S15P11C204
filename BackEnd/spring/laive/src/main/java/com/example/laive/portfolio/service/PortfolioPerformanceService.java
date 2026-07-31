package com.example.laive.portfolio.service;

import com.example.laive.portfolio.repository.PortfolioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실적 스냅샷(avg_viewers 등 5컬럼) 갱신의 단일 진입점이다.
 *
 * <p>DDL 주석은 "배치로 갱신한다"고만 적혀 있고 그 배치가 없었다 — 안 만들면
 * 모든 포트폴리오가 영원히 0으로 표시된다. 두 갈래로 채운다:
 * 본인 조회·생성 시 즉시 재계산(항상 신선), 새벽 일 배치로 전체 재계산
 * (검색 목록에 뜨는 타인의 숫자도 하루 안에는 맞도록).
 */
@Service
public class PortfolioPerformanceService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioPerformanceService.class);

    private final PortfolioRepository repository;

    public PortfolioPerformanceService(PortfolioRepository repository) {
        this.repository = repository;
    }

    /** 한 스트리머의 스냅샷을 재계산한다. 포트폴리오가 없으면 아무 일도 없다. */
    @Transactional
    public void refresh(long streamerId) {
        repository.refreshPerformance(streamerId);
    }

    /** 트래픽이 가장 적은 새벽에 전체를 갱신한다. 방송 시각 지표라 KST 기준이다. */
    @Scheduled(cron = "0 10 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void refreshAll() {
        int updated = repository.refreshAllPerformance();
        log.info("포트폴리오 실적 스냅샷 일괄 갱신: {}건", updated);
    }
}
