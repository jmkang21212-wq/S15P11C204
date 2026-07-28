package com.example.laive.broadcast.session.service;

import com.example.laive.broadcast.session.domain.EndBroadcastSessionResult;
import com.example.laive.broadcast.session.domain.StartBroadcastSessionCommand;
import com.example.laive.broadcast.session.domain.StartBroadcastSessionResult;
import com.example.laive.broadcast.session.exception.BroadcastSessionException;
import org.springframework.stereotype.Service;

/**
 * Redis lifecycle lock을 획득한 뒤 짧은 DB 트랜잭션을 호출하는 공개 Command Service다.
 */
@Service
public class BroadcastSessionCommandService {

    private final BroadcastLifecycleLock lifecycleLock;
    private final BroadcastSessionTransactionService transactionService;

    public BroadcastSessionCommandService(
            BroadcastLifecycleLock lifecycleLock,
            BroadcastSessionTransactionService transactionService
    ) {
        this.lifecycleLock = lifecycleLock;
        this.transactionService = transactionService;
    }

    public StartBroadcastSessionResult start(
            long streamerId,
            StartBroadcastSessionCommand command
    ) {
        return lifecycleLock.execute(
                streamerId,
                BroadcastSessionException::alreadyLive,
                () -> transactionService.start(streamerId, command)
        );
    }

    public EndBroadcastSessionResult end(long streamerId, long sessionId) {
        return lifecycleLock.execute(
                streamerId,
                BroadcastSessionException::stateConflict,
                () -> transactionService.end(streamerId, sessionId)
        );
    }
}

