package vn.edu.hust.kat.agentservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmActionRequest {

    @JsonProperty("proposal_id")
    @JsonAlias({"proposalId"})
    private String proposalId;

    @JsonProperty("confirmation_token")
    @JsonAlias({"confirmationToken"})
    private String confirmationToken;

    @JsonProperty("approved")
    private boolean approved;

    public String resolveProposalId() {
        if (proposalId != null && !proposalId.isBlank()) {
            return proposalId.trim();
        }
        if (confirmationToken != null && !confirmationToken.isBlank()) {
            return confirmationToken.trim();
        }
        return null;
    }
}
