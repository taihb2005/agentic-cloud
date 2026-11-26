package vn.edu.hust.kat.agentservice.dto.conversation;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.edu.hust.kat.agentservice.entity.ChatConversation;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSummaryDto {

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

    public static ConversationSummaryDto from(ChatConversation conv) {
        return ConversationSummaryDto.builder()
                .id(conv.getId())
                .title(conv.getTitle())
                .status(conv.getStatus() != null ? conv.getStatus().name() : null)
                .activeLeafMessageId(conv.getActiveLeafMessage() != null ? conv.getActiveLeafMessage().getId() : null)
                .currentTurnNumber(conv.getCurrentTurnNumber())
                .lastMessageAt(conv.getLastMessageAt())
                .createdAt(conv.getCreatedAt())
                .updatedAt(conv.getUpdatedAt())
                .build();
    }
}
