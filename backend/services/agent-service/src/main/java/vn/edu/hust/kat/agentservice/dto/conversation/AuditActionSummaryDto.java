package vn.edu.hust.kat.agentservice.dto.conversation;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.edu.hust.kat.agentservice.entity.AgentAuditLog;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditActionSummaryDto {

    @JsonProperty("audit_log_id")
    private UUID auditLogId;

    @JsonProperty("turn_id")
    private UUID turnId;

    @JsonProperty("message_id")
    private UUID messageId;

    @JsonProperty("tool_call_id")
    private String toolCallId;

    @JsonProperty("execution_order")
    private Integer executionOrder;

    @JsonProperty("action_type")
    private String actionType;

    @JsonProperty("tool_name")
    private String toolName;

    @JsonProperty("target_node_id")
    private String targetNodeId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("hitl_status")
    private String hitlStatus;

    @JsonProperty("undo_status")
    private String undoStatus;

    @JsonProperty("undone_at")
    private Instant undoneAt;

    @JsonProperty("undone_by_log_id")
    private UUID undoneByLogId;

    @JsonProperty("created_at")
    private Instant createdAt;

    public static AuditActionSummaryDto from(AgentAuditLog log) {
        return AuditActionSummaryDto.builder()
                .auditLogId(log.getId())
                .turnId(log.getTurnId())
                .messageId(log.getMessage() != null ? log.getMessage().getId() : null)
                .toolCallId(log.getToolCallId())
                .executionOrder(log.getExecutionOrder())
                .actionType(log.getActionType())
                .toolName(log.getToolName())
                .targetNodeId(log.getTargetNodeId())
                .status(log.getStatus() != null ? log.getStatus().name() : null)
                .hitlStatus(log.getHitlStatus() != null ? log.getHitlStatus().name() : null)
                .undoStatus(log.getUndoStatus() != null ? log.getUndoStatus().name() : null)
                .undoneAt(log.getUndoneAt())
                .undoneByLogId(log.getUndoneByLog() != null ? log.getUndoneByLog().getId() : null)
                .createdAt(log.getCreatedAt())
                .build();
    }
}
