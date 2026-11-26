package vn.edu.hust.kat.agentservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.edu.hust.kat.agentservice.dto.conversation.AuditActionSummaryDto;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    @JsonProperty("conversation_id")
    private String conversationId;

    @JsonProperty("turn_id")
    private UUID turnId;

    @JsonProperty("reply")
    private String reply;

    @JsonProperty("pending_proposals")
    private List<ActionProposalDto> pendingProposals;

    @JsonProperty("undoable_actions")
    private List<AuditActionSummaryDto> undoableActions;
}
