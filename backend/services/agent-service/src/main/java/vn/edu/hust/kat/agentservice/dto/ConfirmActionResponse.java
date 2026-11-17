package vn.edu.hust.kat.agentservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConfirmActionResponse {

    @JsonProperty("proposal_id")
    private String proposalId;

    @JsonProperty("action_type")
    private String actionType;

    @JsonProperty("target_node_id")
    private String targetNodeId;

    @JsonProperty("approved")
    private boolean approved;

    @JsonProperty("status")
    private String status;

    @JsonProperty("confirmation_token")
    private String confirmationToken;

    @JsonProperty("message")
    private String message;
}
