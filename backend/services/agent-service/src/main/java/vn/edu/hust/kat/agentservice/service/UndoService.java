package vn.edu.hust.kat.agentservice.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hust.kat.agentservice.dto.undo.TurnUndoResponse;
import vn.edu.hust.kat.agentservice.dto.undo.UndoActionResponse;
import vn.edu.hust.kat.agentservice.entity.AgentAuditLog;
import vn.edu.hust.kat.agentservice.guardrail.snapshot.body.InverseActionType;
import vn.edu.hust.kat.agentservice.guardrail.snapshot.payload.UndoSnapshotPayload;
import vn.edu.hust.kat.agentservice.guardrail.undo.Undoable;
import vn.edu.hust.kat.agentservice.repository.AgentAuditLogRepository;
import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.common.api.exception.ResourceNotFoundException;

import java.time.Instant;
import java.util.*;

@Service
@Slf4j
public class UndoService {

    private final AgentAuditLogRepository auditLogRepository;
    private final ChatConversationService chatConversationService;
    private final JsonMapper jsonMapper;
    private final Map<String, Undoable> undoablesByActionType;

    public UndoService(
            AgentAuditLogRepository auditLogRepository,
            ChatConversationService chatConversationService,
            JsonMapper jsonMapper,
            List<Undoable> undoables
    ) {
        this.auditLogRepository = auditLogRepository;
        this.chatConversationService = chatConversationService;
        this.jsonMapper = jsonMapper;
        Map<String, Undoable> registry = new HashMap<>();
        if (undoables != null) {
            for (Undoable undoable : undoables) {
                for (String actionType : undoable.supportedActionTypes()) {
                    registry.put(actionType, undoable);
                }
            }
        }
        this.undoablesByActionType = Collections.unmodifiableMap(registry);
    }

    @Transactional(noRollbackFor = RuntimeException.class)
    public UndoActionResponse undoAuditLog(UUID userId, UUID auditLogId) {
        AgentAuditLog originalLog = auditLogRepository.findByIdAndUserId(auditLogId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy audit log: " + auditLogId));

        validateUndoable(originalLog);
        return executeSingleUndo(userId, originalLog);
    }

    @Transactional
    public TurnUndoResponse undoTurn(
            UUID userId,
            UUID conversationId,
            UUID turnId,
            boolean rewindConversation
    ) {
        chatConversationService.requireOwnedConversation(userId, conversationId);

        List<AgentAuditLog> undoableLogs = auditLogRepository
                .findByConversationIdAndTurnIdAndUserIdAndUndoStatusOrderByExecutionOrderDesc(
                        conversationId,
                        turnId,
                        userId,
                        AgentAuditLog.UndoStatus.UNDOABLE
                );

        List<UndoActionResponse> results = new ArrayList<>();
        int undoneCount = 0;
        int failedCount = 0;

        // Fail-Fast LIFO execution
        for (AgentAuditLog auditLog : undoableLogs) {
            UndoActionResponse itemResult = executeSingleUndoNoThrow(userId, auditLog);
            results.add(itemResult);
            if (AgentAuditLog.UndoStatus.UNDONE.name().equals(itemResult.getUndoStatus())) {
                undoneCount++;
            } else {
                failedCount++;
                log.warn("Fail-fast stopping turn LIFO undo for turn={} at log={}: {}",
                        turnId, auditLog.getId(), itemResult.getMessage());
                break;
            }
        }

        boolean rewound = false;
        UUID newLeafId = null;
        if (failedCount == 0 && rewindConversation) {
            newLeafId = chatConversationService.rewindBeforeTurn(userId, conversationId, turnId);
            rewound = true;
        }

        String summaryMessage;
        if (failedCount > 0) {
            summaryMessage = String.format(
                    "Hoàn tác turn dừng do lỗi (đã hoàn tác %d/%d hành động, lỗi %d). Nhánh hội thoại chưa được rewind.",
                    undoneCount, undoableLogs.size(), failedCount
            );
        } else if (undoableLogs.isEmpty()) {
            summaryMessage = rewound
                    ? "Turn không có hành động cần hoàn tác; đã rewind nhánh hội thoại."
                    : "Turn không có hành động nào ở trạng thái UNDOABLE.";
        } else {
            summaryMessage = String.format(
                    "Đã hoàn tác thành công %d hành động theo thứ tự LIFO%s.",
                    undoneCount,
                    rewound ? " và rewind nhánh hội thoại" : ""
            );
        }

        return TurnUndoResponse.builder()
                .conversationId(conversationId)
                .turnId(turnId)
                .totalActions(undoableLogs.size())
                .undoneCount(undoneCount)
                .failedCount(failedCount)
                .conversationRewound(rewound)
                .newActiveLeafMessageId(newLeafId)
                .results(results)
                .message(summaryMessage)
                .build();
    }

    private void validateUndoable(AgentAuditLog originalLog) {
        if (originalLog.getStatus() != AgentAuditLog.Status.SUCCESS) {
            throw new BadRequestException("Chỉ có thể hoàn tác hành động đã thực thi thành công (status=" + originalLog.getStatus() + ")");
        }
        if (originalLog.getUndoStatus() == AgentAuditLog.UndoStatus.UNDONE) {
            throw new BadRequestException("Hành động này đã được hoàn tác trước đó");
        }
        if (originalLog.getUndoStatus() != AgentAuditLog.UndoStatus.UNDOABLE) {
            throw new BadRequestException("Hành động này không hỗ trợ hoàn tác (undoStatus=" + originalLog.getUndoStatus() + ")");
        }
        if (originalLog.getUndoPayload() == null || originalLog.getUndoPayload().isBlank()) {
            throw new BadRequestException("Hành động này không có dữ liệu snapshot để hoàn tác");
        }
    }

    private UndoActionResponse executeSingleUndo(UUID userId, AgentAuditLog originalLog) {
        UndoSnapshotPayload payload = deserializePayload(originalLog.getUndoPayload());
        if (!payload.isSupportUndo() || payload.getInverseActionType() == InverseActionType.NONE) {
            throw new BadRequestException("Snapshot đánh dấu hành động không hỗ trợ hoàn tác");
        }

        String actionType = (payload.getActionType() != null && !payload.getActionType().isBlank())
                ? payload.getActionType()
                : originalLog.getActionType();
        Undoable undoable = undoablesByActionType.get(actionType);
        if (undoable == null) {
            throw new BadRequestException("Không tìm thấy Undoable hỗ trợ actionType=" + actionType);
        }

        try {
            String undoMessage = undoable.revert(userId, payload);
            Instant now = Instant.now();

            AgentAuditLog compensatingLog = AgentAuditLog.builder()
                    .userId(userId)
                    .conversation(originalLog.getConversation())
                    .turnId(originalLog.getTurnId())
                    .message(originalLog.getMessage())
                    .toolCallId(originalLog.getToolCallId())
                    .executionOrder(originalLog.getExecutionOrder())
                    .actionType("UNDO_" + originalLog.getActionType())
                    .toolName("undo:" + originalLog.getToolName())
                    .targetNodeId(originalLog.getTargetNodeId())
                    .inputPayload("undoOf=" + originalLog.getId() + ", inverseAction=" + payload.getInverseActionType())
                    .status(AgentAuditLog.Status.SUCCESS)
                    .hitlStatus(AgentAuditLog.HitlStatus.NOT_REQUIRED)
                    .undoStatus(AgentAuditLog.UndoStatus.NOT_APPLICABLE)
                    .reason(undoMessage)
                    .build();
            compensatingLog = auditLogRepository.save(compensatingLog);

            originalLog.setUndoStatus(AgentAuditLog.UndoStatus.UNDONE);
            originalLog.setUndoneAt(now);
            originalLog.setUndoneByLog(compensatingLog);
            auditLogRepository.save(originalLog);

            log.info("Successfully undid auditLog={} with compensatingLog={} ({})",
                    originalLog.getId(), compensatingLog.getId(), payload.getInverseActionType());

            return UndoActionResponse.builder()
                    .auditLogId(originalLog.getId())
                    .compensatingLogId(compensatingLog.getId())
                    .actionType(originalLog.getActionType())
                    .inverseActionType(payload.getInverseActionType().name())
                    .undoStatus(AgentAuditLog.UndoStatus.UNDONE.name())
                    .undoneAt(now)
                    .message(undoMessage)
                    .build();
        } catch (Exception e) {
            AgentAuditLog failedCompLog = AgentAuditLog.builder()
                    .userId(userId)
                    .conversation(originalLog.getConversation())
                    .turnId(originalLog.getTurnId())
                    .message(originalLog.getMessage())
                    .toolCallId(originalLog.getToolCallId())
                    .executionOrder(originalLog.getExecutionOrder())
                    .actionType("UNDO_" + originalLog.getActionType())
                    .toolName("undo:" + originalLog.getToolName())
                    .targetNodeId(originalLog.getTargetNodeId())
                    .inputPayload("undoOf=" + originalLog.getId() + ", inverseAction=" + payload.getInverseActionType())
                    .status(AgentAuditLog.Status.FAILED)
                    .hitlStatus(AgentAuditLog.HitlStatus.NOT_REQUIRED)
                    .undoStatus(AgentAuditLog.UndoStatus.NOT_APPLICABLE)
                    .reason(e.getMessage())
                    .build();
            auditLogRepository.save(failedCompLog);

            originalLog.setUndoStatus(AgentAuditLog.UndoStatus.UNDO_FAILED);
            auditLogRepository.save(originalLog);

            log.error("Failed to undo auditLog={}: {}", originalLog.getId(), e.getMessage());
            throw e;
        }
    }

    private UndoActionResponse executeSingleUndoNoThrow(UUID userId, AgentAuditLog originalLog) {
        try {
            return executeSingleUndo(userId, originalLog);
        } catch (Exception e) {
            return UndoActionResponse.builder()
                    .auditLogId(originalLog.getId())
                    .actionType(originalLog.getActionType())
                    .undoStatus(AgentAuditLog.UndoStatus.UNDO_FAILED.name())
                    .message("Hoàn tác thất bại: " + e.getMessage())
                    .build();
        }
    }

    private UndoSnapshotPayload deserializePayload(String rawJson) {
        try {
            return jsonMapper.readValue(rawJson, UndoSnapshotPayload.class);
        } catch (Exception e) {
            throw new BadRequestException("Không thể đọc dữ liệu undo snapshot: " + e.getMessage());
        }
    }
}
