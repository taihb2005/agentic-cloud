package vn.edu.hust.kat.agentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionProposalDto {

    private String proposalId;
    private String actionType; // e.g. HARD_DELETE, PUBLIC_SHARE
    private List<String> targetNodeIds;
    private String description;
    private String confirmationToken;
    private boolean requiresExplicitConfirmation;
}
