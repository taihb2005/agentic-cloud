package vn.edu.hust.kat.agentservice.dto.conversation;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.edu.hust.kat.agentservice.entity.ChatMessage;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("turn_id")
    private UUID turnId;

    @JsonProperty("turn_number")
    private Integer turnNumber;

    @JsonProperty("sequence_number")
    private Integer sequenceNumber;

    @JsonProperty("parent_message_id")
    private UUID parentMessageId;

    @JsonProperty("role")
    private String role;

    @JsonProperty("content")
    private String content;

    @JsonProperty("tool_call_id")
    private String toolCallId;

    @JsonProperty("tool_name")
    private String toolName;

    @JsonProperty("status")
    private String status;

    @JsonProperty("created_at")
    private Instant createdAt;

    public static ChatMessageDto from(ChatMessage entity) {
        return ChatMessageDto.builder()
                .id(entity.getId())
                .turnId(entity.getTurnId())
                .turnNumber(entity.getTurnNumber())
                .sequenceNumber(entity.getSequenceNumber())
                .parentMessageId(entity.getParentMessage() != null ? entity.getParentMessage().getId() : null)
                .role(entity.getRole() != null ? entity.getRole().name() : null)
                .content(entity.getContent())
                .toolCallId(entity.getToolCallId())
                .toolName(entity.getToolName())
                .status(entity.getStatus() != null ? entity.getStatus().name() : null)
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
