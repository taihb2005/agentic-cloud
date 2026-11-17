package vn.edu.hust.kat.agentservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    @JsonProperty("conversation_id")
    private String conversationId;

    @JsonProperty("reply")
    private String reply;

    @JsonProperty("pending_proposals")
    private List<ActionProposalDto> pendingProposals;
}
