package com.example.laive.promise.service;

import com.example.laive.promise.domain.Promise;
import com.example.laive.promise.domain.UpdatePromiseCommand;
import com.example.laive.promise.exception.PromiseException;
import com.example.laive.promise.repository.PromiseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 약속의 확정·무시·상태 변경을 담당한다 (API 10.8.2, 10.8.3).
 *
 * <p>세 동작 모두 "현재 상태를 잠그고 → 전이 규칙을 검사하고 → 저장"하는 같은 형태다.
 * 잠금 안에서 검사해야 같은 약속에 대한 동시 요청이 서로의 검증을 무의미하게 만들지 않는다.
 * 전이 규칙 자체는 {@link Promise}가 갖고 있어 서비스는 흐름만 담당한다.
 */
@Service
@Transactional
public class PromiseCommandService {

    private final PromiseRepository repository;

    public PromiseCommandService(PromiseRepository repository) {
        this.repository = repository;
    }

    /** 감지된 약속을 확정한다. 스트리머가 고친 내용·기한을 함께 반영한다. */
    public Promise confirm(long streamerId, long promiseId, String content, LocalDate dueDate) {
        return save(lock(streamerId, promiseId).confirm(content, dueDate));
    }

    /** 감지된 약속을 무시 처리한다. */
    public void dismiss(long streamerId, long promiseId) {
        save(lock(streamerId, promiseId).dismiss());
    }

    /** 확정된 약속의 상태·내용·기한을 부분 수정한다. 캘린더 드래그도 이 경로를 쓴다. */
    public Promise update(long streamerId, long promiseId, UpdatePromiseCommand command) {
        return save(lock(streamerId, promiseId).applyPatch(command));
    }

    private Promise lock(long streamerId, long promiseId) {
        return repository.findForUpdate(streamerId, promiseId)
                .orElseThrow(PromiseException::notFound);
    }

    private Promise save(Promise promise) {
        if (repository.update(promise) != 1) {
            throw PromiseException.notFound();
        }
        return promise;
    }
}
