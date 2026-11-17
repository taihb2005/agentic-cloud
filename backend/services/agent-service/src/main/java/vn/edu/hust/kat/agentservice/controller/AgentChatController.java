package vn.edu.hust.kat.agentservice.controller;

import dev.langchain4j.service.TokenStream;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.edu.hust.kat.agentservice.context.UserContextManager;
import vn.edu.hust.kat.agentservice.core.CloudAgent;
import vn.edu.hust.kat.agentservice.dto.ActionProposalDto;
import vn.edu.hust.kat.agentservice.dto.ChatRequest;
import vn.edu.hust.kat.agentservice.dto.ChatResponse;
import vn.edu.hust.kat.agentservice.dto.ConfirmActionRequest;
import vn.edu.hust.kat.agentservice.dto.ConfirmActionResponse;
import vn.edu.hust.kat.agentservice.service.ActionConfirmationService;
import vn.edu.hust.kat.common.api.response.ApiResponse;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/agent-service/agent")
@RequiredArgsConstructor
@Slf4j
public class AgentChatController {

    private final CloudAgent cloudAgent;
    private final UserContextManager userContextManager;
    private final ActionConfirmationService actionConfirmationService;

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(
            @Valid @RequestBody ChatRequest request
    ) {
        UUID userUuid = userContextManager.getCurrentUserId();
        String userId = userUuid.toString();
        String conversationId = (request.getConversationId() != null && !request.getConversationId().isBlank())
                ? request.getConversationId().trim()
                : UUID.randomUUID().toString();
        String memoryId = userId + ":" + conversationId;
        log.info("Incoming chat request from user: {}, conversationId: {}, memoryId: {}", userId, conversationId, memoryId);

        String effectiveMessage = buildEffectiveMessage(request.getMessage(), request.getConfirmationToken());
        userContextManager.setCurrentConfirmationToken(request.getConfirmationToken());
        try {
            String reply = cloudAgent.chat(memoryId, effectiveMessage);
            List<ActionProposalDto> pendingProposals = actionConfirmationService.drainPendingProposals(userUuid);

            ChatResponse response = ChatResponse.builder()
                    .conversationId(conversationId)
                    .reply(reply)
                    .pendingProposals(pendingProposals.isEmpty() ? null : pendingProposals)
                    .build();

            return ResponseEntity.ok(ApiResponse.success(response));
        } finally {
            userContextManager.clearCurrentConfirmationToken();
        }
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @RequestParam("message") String message,
            @RequestParam(value = "conversation_id", required = false) String conversationId,
            @RequestParam(value = "confirmation_token", required = false) String confirmationToken
    ) {
        String userId = userContextManager.getCurrentUserIdAsString();
        String sessionId = (conversationId != null && !conversationId.isBlank())
                ? conversationId.trim()
                : "default";
        String memoryId = userId + ":" + sessionId;
        log.info("Incoming streaming chat request from user: {}, conversationId: {}", userId, sessionId);

        String effectiveMessage = buildEffectiveMessage(message, confirmationToken);
        SseEmitter emitter = new SseEmitter(180_000L); // 3 minutes timeout
        TokenStream tokenStream = cloudAgent.chatStream(memoryId, effectiveMessage);

        tokenStream
                .onNext(token -> {
                    try {
                        emitter.send(SseEmitter.event().data(token));
                    } catch (IOException e) {
                        log.warn("Failed to send token to SSE emitter: {}", e.getMessage());
                    }
                })
                .onComplete(response -> {
                    try {
                        emitter.send(SseEmitter.event().name("complete").data("[DONE]"));
                        emitter.complete();
                    } catch (IOException e) {
                        log.warn("Failed to complete SSE emitter: {}", e.getMessage());
                    }
                })
                .onError(error -> {
                    log.error("Streaming error: {}", error.getMessage());
                    emitter.completeWithError(error);
                })
                .start();

        return emitter;
    }

    @PostMapping("/actions/confirm")
    public ResponseEntity<ApiResponse<ConfirmActionResponse>> confirmAction(
            @Valid @RequestBody ConfirmActionRequest request
    ) {
        UUID userId = userContextManager.getCurrentUserId();
        String proposalId = request.resolveProposalId();
        log.info("User {} confirming proposal {}: approved={}", userId, proposalId, request.isApproved());

        ConfirmActionResponse response = actionConfirmationService.confirmProposal(
                userId,
                proposalId,
                request.isApproved()
        );

        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }

    private String buildEffectiveMessage(String rawMessage, String confirmationToken) {
        if (confirmationToken != null && !confirmationToken.isBlank()) {
            return rawMessage + "\n[CONFIRMATION_TOKEN: " + confirmationToken.trim() + "]";
        }
        return rawMessage;
    }
}
