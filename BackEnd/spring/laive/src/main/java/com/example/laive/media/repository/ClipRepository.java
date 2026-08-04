package com.example.laive.media.repository;

import com.example.laive.media.domain.ClipJobRow;
import com.example.laive.media.domain.ClipTarget;

import java.util.List;
import java.util.Optional;

/**
 * 클립 작업 대상과 결과 반영의 영속성 Port다.
 */
public interface ClipRepository {

    /** 로그인 스트리머의 클립 대기 하이라이트를 녹화본이 준비된 것만 골라 준다. */
    List<ClipJobRow> findPendingJobs(long streamerId);

    /** 업로드 대상의 현재 상태를 소유권과 함께 읽는다. */
    Optional<ClipTarget> findTarget(long streamerId, long highlightId);

    /** 클립 저장을 반영한다. PENDING이 아니면 0을 돌려준다. */
    int markUploaded(long highlightId, String clipPath, String thumbnailPath);

    /**
     * 실패를 누적한다. 시도 횟수가 상한에 닿으면 FAILED로 확정해 앱이 같은 작업을
     * 다시 받지 않게 한다.
     */
    int recordFailure(long highlightId, String reason, int maxAttempts);

    /** 재시도해도 결과가 같은 실패를 즉시 확정한다(녹화본 없음 등). */
    int markSkipped(long highlightId, String reason);
}
