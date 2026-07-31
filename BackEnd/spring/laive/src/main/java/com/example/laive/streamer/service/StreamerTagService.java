package com.example.laive.streamer.service;

import com.example.laive.streamer.exception.StreamerProfileException;
import com.example.laive.streamer.repository.StreamerProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

/**
 * 스트리머 태그 교체의 단일 원천이다.
 *
 * <p>프로필 수정(API 6.6)과 포트폴리오 생성(API 12)이 모두 태그 선택을 받는데,
 * 검증·저장 규칙이 갈라지면 같은 태그가 화면마다 다르게 저장될 수 있다.
 * 둘 다 이 서비스를 거친다.
 */
@Service
@Transactional
public class StreamerTagService {

    private final StreamerProfileRepository repository;

    public StreamerTagService(StreamerProfileRepository repository) {
        this.repository = repository;
    }

    /**
     * 태그를 통째로 교체한다. 배열 순서가 display_order(노출 순서)가 된다.
     *
     * <p>null은 "전부 비움"으로 취급한다. 부분 추가·삭제 API는 없다 — 선택 UI가
     * 항상 전체 선택 상태를 보내는 구조라 교체가 자연스럽다.
     */
    public void replaceTags(long streamerId, List<Long> tagIds) {
        List<Long> ids = tagIds == null ? List.of() : tagIds;

        if (ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw StreamerProfileException.invalidTag();
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw StreamerProfileException.invalidTag();
        }
        if (!ids.isEmpty() && repository.countTagsByIds(ids) != ids.size()) {
            throw StreamerProfileException.invalidTag();
        }

        repository.deleteTags(streamerId);
        if (!ids.isEmpty()) {
            repository.insertTags(streamerId, ids);
        }
    }
}
