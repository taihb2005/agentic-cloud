package vn.edu.hust.kat.agentservice.controller;

import dev.langchain4j.service.TokenStream;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.edu.hust.kat.agentservice.context.ChatTurnContext;
import vn.edu.hust.kat.agentservice.context.UserContextManager;
import vn.edu.hust.kat.agentservice.core.CloudAgent;
import vn.edu.hust.kat.agentservice.dto.ActionProposalDto;
import vn.edu.hust.kat.agentservice.dto.ChatRequest;
import vn.edu.hust.kat.agentservice.dto.ChatResponse;
import vn.edu.hust.kat.agentservice.dto.ConfirmActionRequest;
import vn.edu.hust.kat.agentservice.dto.ConfirmActionResponse;
import vn.edu.hust.kat.agentservice.dto.conversation.AuditActionSummaryDto;
import vn.edu.hust.kat.agentservice.security.UserPrincipal;
import vn.edu.hust.kat.agentservice.service.ActionConfirmationService;
import vn.edu.hust.kat.agentservice.service.ChatConversationService;
import vn.edu.hust.kat.common.api.response.ApiResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/agent-service/agent")
@RequiredArgsConstructor
@Slf4j
public class AgentChatController {

    private final CloudAgent cloudAgent;
    private final UserContextManager userContextManager;
    private final ActionConfirmationService actionConfirmationService;
    private final ChatConversationService chatConversationService;

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody ChatRequest request
    ) {
        UUID userUuid = userPrincipal != null ? userPrincipal.getUserId() : null;
        ChatTurnContext turnContext = chatConversationService.startTurn(
                userUuid,
                request.getConversationId(),
                request.getMessage()
        );
        String conversationId = turnContext.getConversationId().toString();
        String memoryId = ChatConversationService.buildMemoryId(userUuid, turnContext.getConversationId());
        log.info("Incoming chat request from user: {}, conversationId: {}, turnId: {}",
                userUuid, conversationId, turnContext.getTurnId());

        String effectiveMessage = buildEffectiveMessage(request.getMessage(), request.getConfirmationToken());
        userContextManager.setCurrentConfirmationToken(request.getConfirmationToken());
        userContextManager.setCurrentTurnContext(turnContext);
        try {
            String reply = cloudAgent.chat(memoryId, effectiveMessage);
            List<ActionProposalDto> pendingProposals = actionConfirmationService.drainPendingProposals(userUuid);
            List<AuditActionSummaryDto> undoableActions =
                    chatConversationService.completeTurn(turnContext, request.getMessage(), reply);

            ChatResponse response = ChatResponse.builder()
                    .conversationId(conversationId)
                    .turnId(turnContext.getTurnId())
                    .reply(reply)
                    .pendingProposals(pendingProposals.isEmpty() ? null : pendingProposals)
                    .undoableActions(undoableActions.isEmpty() ? null : undoableActions)
                    .build();

            return ResponseEntity.ok(ApiResponse.success(response));
        } finally {
            userContextManager.clearCurrentTurnContext();
            userContextManager.clearCurrentConfirmationToken();
        }
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam("message") String message,
            @RequestParam(value = "conversation_id", required = false) String conversationId,
            @RequestParam(value = "confirmation_token", required = false) String confirmationToken
    ) {
        UUID userUuid = userPrincipal != null ? userPrincipal.getUserId() : null;
        ChatTurnContext turnContext = chatConversationService.startTurn(userUuid, conversationId, message);
        String resolvedConvId = turnContext.getConversationId().toString();
        String memoryId = ChatConversationService.buildMemoryId(userUuid, turnContext.getConversationId());
        log.info("Incoming streaming chat request from user: {}, conversationId: {}, turnId: {}",
                userUuid, resolvedConvId, turnContext.getTurnId());

        String effectiveMessage = buildEffectiveMessage(message, confirmationToken);
        SseEmitter emitter = new SseEmitter(180_000L); // 3 minutes timeout

        userContextManager.setCurrentConfirmationToken(confirmationToken);
        userContextManager.setCurrentTurnContext(turnContext);
        try {
            TokenStream tokenStream = cloudAgent.chatStream(memoryId, effectiveMessage);
            StringBuilder replyBuffer = new StringBuilder();

            tokenStream
                    .onNext(token -> {
                        replyBuffer.append(token);
                        try {
                            emitter.send(SseEmitter.event().data(token));
                        } catch (IOException e) {
                            log.warn("Failed to send token to SSE emitter: {}", e.getMessage());
                        }
                    })
                    .onComplete(response -> {
                        try {
                            List<AuditActionSummaryDto> undoableActions =
                                    chatConversationService.completeTurn(turnContext, message, replyBuffer.toString());
                            emitter.send(SseEmitter.event().name("meta").data(Map.of(
                                    "conversation_id", resolvedConvId,
                                    "turn_id", turnContext.getTurnId().toString(),
                                    "undoable_actions", undoableActions
                            )));
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
        } finally {
            userContextManager.clearCurrentTurnContext();
            userContextManager.clearCurrentConfirmationToken();
        }

        return emitter;
    }

    @PostMapping("/actions/confirm")
    public ResponseEntity<ApiResponse<ConfirmActionResponse>> confirmAction(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody ConfirmActionRequest request
    ) {
        UUID userId = userPrincipal != null ? userPrincipal.getUserId() : null;
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
