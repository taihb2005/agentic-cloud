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
import vn.edu.hust.kat.agentservice.dto.ChatRequest;
import vn.edu.hust.kat.agentservice.dto.ChatResponse;
import vn.edu.hust.kat.agentservice.dto.ConfirmActionRequest;
import vn.edu.hust.kat.agentservice.service.AuditService;
import vn.edu.hust.kat.common.api.response.ApiResponse;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
@Slf4j
public class AgentChatController {

    private final CloudAgent cloudAgent;
    private final UserContextManager userContextManager;
    private final AuditService auditService;

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(@Valid @RequestBody ChatRequest request) {
        String userId = userContextManager.getCurrentUserIdAsString();
        log.info("Incoming chat request from user: {}", userId);

        String reply = cloudAgent.chat(userId, request.getMessage());

        ChatResponse response = ChatResponse.builder()
                .conversationId(request.getConversationId() != null ? request.getConversationId() : UUID.randomUUID().toString())
                .reply(reply)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestParam("message") String message) {
        String userId = userContextManager.getCurrentUserIdAsString();
        log.info("Incoming streaming chat request from user: {}", userId);

        SseEmitter emitter = new SseEmitter(180_000L); // 3 minutes timeout
        TokenStream tokenStream = cloudAgent.chatStream(userId, message);

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
    public ResponseEntity<ApiResponse<String>> confirmAction(@Valid @RequestBody ConfirmActionRequest request) {
        UUID userId = userContextManager.getCurrentUserId();
        log.info("User {} confirming action token {}: approved={}", userId, request.getConfirmationToken(), request.isApproved());

        auditService.recordToolAction(
                userId,
                "HITL_CONFIRMATION",
                "confirmAction",
                null,
                "token=" + request.getConfirmationToken(),
                null,
                request.isApproved() ? "APPROVED" : "REJECTED"
        );

        String message = request.isApproved()
                ? "Thao tác nhạy cảm đã được phê duyệt và thực thi thành công."
                : "Thao tác đã bị hủy bỏ theo yêu cầu của bạn.";

        return ResponseEntity.ok(ApiResponse.success(message));
    }
}
