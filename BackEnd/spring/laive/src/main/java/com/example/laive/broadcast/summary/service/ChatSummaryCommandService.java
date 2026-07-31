package com.example.laive.broadcast.summary.service;

import com.example.laive.broadcast.realtime.domain.ChatAiSummaryCall;
import com.example.laive.broadcast.realtime.domain.SummaryMode;
import com.example.laive.broadcast.realtime.integration.ChatAiGateway;
import com.example.laive.broadcast.session.service.BroadcastSessionQueryService;
import com.example.laive.broadcast.summary.dto.response.ChatSummaryCreateResponse;
import com.example.laive.broadcast.summary.exception.ChatSummaryException;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * 수동(MANUAL) 여론 요약 생성이다(계약 §2 — 프론트 버튼은 MANUAL로 호출).
 *
 * <p>이 클래스에 @Transactional을 두지 않는 이유: LLM 응답 대기(최대 15초)가
 * DB 커넥션을 잡고 있으면 안 된다. 저장 트랜잭션은 {@link ChatSummaryBriefingService}
 * 안에서만 짧게 열린다.
 */
@Service
public class ChatSummaryCommandService {

    /**
     * chat-ai의 최소 채팅 기준(계약 §2의 SUMMARY_MIN_CHATS). UNKNOWN_BROADCAST
     * (ingest 이력 없음)를 "아직 채팅이 없다"로 눌러쓸 때의 required 표기용이다 —
     * 프론트가 다뤄야 할 status를 4종으로 유지한다.
     */
    private static final int REQUIRED_MIN_CHATS = 30;

    private final BroadcastSessionQueryService sessionQueryService;
    private final ChatAiGateway chatAiGateway;
    private final ChatSummaryBriefingService briefingService;
    private final Clock clock;

    public ChatSummaryCommandService(
            BroadcastSessionQueryService sessionQueryService,
            ChatAiGateway chatAiGateway,
            ChatSummaryBriefingService briefingService,
            Clock clock
    ) {
        this.sessionQueryService = sessionQueryService;
        this.chatAiGateway = chatAiGateway;
        this.briefingService = briefingService;
        this.clock = clock;
    }

    /**
     * 요약을 새로 생성해 브리핑으로 저장하고 결과를 돌려준다. 세션 조회가 소유권
     * 검증을 겸하므로 타인 세션은 404로 숨겨진다.
     *
     * @throws ChatSummaryException AI 미연결·타임아웃·5xx 등 전송 실패(503)
     */
    public ChatSummaryCreateResponse create(long streamerId, long sessionId) {
        sessionQueryService.getSession(streamerId, sessionId);

        ChatAiSummaryCall call = chatAiGateway.requestSummary(sessionId, SummaryMode.MANUAL);
        return switch (call) {
            case ChatAiSummaryCall.Success success -> {
                briefingService.append(sessionId, success.summary());
                yield ChatSummaryCreateResponse.created(success.summary());
            }
            case ChatAiSummaryCall.Cooldown cooldown -> ChatSummaryCreateResponse.cooldown(
                    cooldown.retryAfterSec(),
                    clock.instant().plusSeconds(cooldown.retryAfterSec()));
            case ChatAiSummaryCall.InFlight inFlight -> ChatSummaryCreateResponse.inFlight();
            case ChatAiSummaryCall.NotEnoughChats notEnough ->
                    ChatSummaryCreateResponse.notEnoughChats(notEnough.chatCount(), notEnough.required());
            // ingest 이력이 없는 방송 = 아직 요약할 채팅이 없다. 프론트 입장에선 같은 상태다.
            case ChatAiSummaryCall.UnknownBroadcast unknown ->
                    ChatSummaryCreateResponse.notEnoughChats(0, REQUIRED_MIN_CHATS);
            case ChatAiSummaryCall.Failure failure -> throw ChatSummaryException.unavailable();
        };
    }
}
