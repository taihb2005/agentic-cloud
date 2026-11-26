package vn.edu.hust.kat.agentservice.dto.undo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UndoActionResponse {

    @JsonProperty("audit_log_id")
    private UUID auditLogId;

    @JsonProperty("compensating_log_id")
    private UUID compensatingLogId;

    @JsonProperty("action_type")
    private String actionType;

    @JsonProperty("inverse_action_type")
    private String inverseActionType;

    @JsonProperty("undo_status")
    private String undoStatus;

    @JsonProperty("undone_at")
    private Instant undoneAt;

    @JsonProperty("message")
    private String message;
}
