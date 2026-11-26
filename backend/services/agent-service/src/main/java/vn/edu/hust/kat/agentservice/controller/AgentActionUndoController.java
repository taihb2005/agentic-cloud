package vn.edu.hust.kat.agentservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.hust.kat.agentservice.dto.undo.TurnUndoResponse;
import vn.edu.hust.kat.agentservice.dto.undo.UndoActionResponse;
import vn.edu.hust.kat.agentservice.security.UserPrincipal;
import vn.edu.hust.kat.agentservice.service.UndoService;
import vn.edu.hust.kat.common.api.response.ApiResponse;

import java.util.UUID;

@RestController
@RequestMapping("/agent-service/agent")
@RequiredArgsConstructor
@Slf4j
public class AgentActionUndoController {

    private final UndoService undoService;

    @PostMapping("/actions/{auditLogId}/undo")
    public ResponseEntity<ApiResponse<UndoActionResponse>> undoSingleAction(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID auditLogId
    ) {
        UUID userId = userPrincipal != null ? userPrincipal.getUserId() : null;
        log.info("User {} requesting single undo for auditLogId={}", userId, auditLogId);
        UndoActionResponse response = undoService.undoAuditLog(userId, auditLogId);
        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }

    @PostMapping("/conversations/{conversationId}/turns/{turnId}/undo")
    public ResponseEntity<ApiResponse<TurnUndoResponse>> undoTurn(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID conversationId,
            @PathVariable UUID turnId,
            @RequestParam(name = "rewind", defaultValue = "true") boolean rewind
    ) {
        UUID userId = userPrincipal != null ? userPrincipal.getUserId() : null;
        log.info("User {} requesting LIFO turn undo for conversationId={}, turnId={}, rewind={}",
                userId, conversationId, turnId, rewind);
        TurnUndoResponse response = undoService.undoTurn(userId, conversationId, turnId, rewind);
        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }
}
