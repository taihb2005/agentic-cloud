package vn.edu.hust.kat.agentservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.hust.kat.agentservice.dto.conversation.ConversationDetailDto;
import vn.edu.hust.kat.agentservice.dto.conversation.ConversationSummaryDto;
import vn.edu.hust.kat.agentservice.dto.conversation.CreateConversationRequest;
import vn.edu.hust.kat.agentservice.dto.conversation.UpdateConversationRequest;
import vn.edu.hust.kat.agentservice.security.UserPrincipal;
import vn.edu.hust.kat.agentservice.service.ChatConversationService;
import vn.edu.hust.kat.common.api.response.ApiResponse;

import java.util.UUID;

@RestController
@RequestMapping("/agent-service/agent/conversations")
@RequiredArgsConstructor
@Slf4j
public class ChatConversationController {

    private final ChatConversationService conversationService;

    @PostMapping
    public ResponseEntity<ApiResponse<ConversationSummaryDto>> createConversation(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody(required = false) CreateConversationRequest request
    ) {
        UUID userId = userPrincipal != null ? userPrincipal.getUserId() : null;
        String title = request != null ? request.getTitle() : null;
        ConversationSummaryDto created = conversationService.createConversation(userId, title);
        return ResponseEntity.ok(ApiResponse.success("Tạo hội thoại thành công", created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ConversationSummaryDto>>> listConversations(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID userId = userPrincipal != null ? userPrincipal.getUserId() : null;
        Page<ConversationSummaryDto> result = conversationService.listConversations(userId, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<ConversationDetailDto>> getConversationDetail(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID conversationId
    ) {
        UUID userId = userPrincipal != null ? userPrincipal.getUserId() : null;
        ConversationDetailDto detail = conversationService.getConversationDetail(userId, conversationId);
        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    @PatchMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<ConversationSummaryDto>> updateConversation(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID conversationId,
            @Valid @RequestBody UpdateConversationRequest request
    ) {
        UUID userId = userPrincipal != null ? userPrincipal.getUserId() : null;
        ConversationSummaryDto updated = conversationService.updateConversation(userId, conversationId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật hội thoại thành công", updated));
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<Void>> deleteConversation(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID conversationId
    ) {
        UUID userId = userPrincipal != null ? userPrincipal.getUserId() : null;
        conversationService.softDeleteConversation(userId, conversationId);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa hội thoại", null));
    }
}
