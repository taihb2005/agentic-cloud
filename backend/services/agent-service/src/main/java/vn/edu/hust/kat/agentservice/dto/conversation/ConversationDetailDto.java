package vn.edu.hust.kat.agentservice.dto.conversation;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDetailDto {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("title")
    private String title;

    @JsonProperty("status")
    private String status;

    @JsonProperty("active_leaf_message_id")
    private UUID activeLeafMessageId;

    @JsonProperty("current_turn_number")
    private Integer currentTurnNumber;

    @JsonProperty("last_message_at")
    private Instant lastMessageAt;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    @JsonProperty("messages")
    private List<ChatMessageDto> messages;

    @JsonProperty("audit_actions")
    private List<AuditActionSummaryDto> auditActions;
}
