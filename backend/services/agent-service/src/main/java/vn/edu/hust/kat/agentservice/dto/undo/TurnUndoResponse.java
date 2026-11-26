package vn.edu.hust.kat.agentservice.dto.undo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TurnUndoResponse {

    @JsonProperty("conversation_id")
    private UUID conversationId;

    @JsonProperty("turn_id")
    private UUID turnId;

    @JsonProperty("total_actions")
    private int totalActions;

    @JsonProperty("undone_count")
    private int undoneCount;

    @JsonProperty("failed_count")
    private int failedCount;

    @JsonProperty("conversation_rewound")
    private boolean conversationRewound;

    @JsonProperty("new_active_leaf_message_id")
    private UUID newActiveLeafMessageId;

    @JsonProperty("results")
    private List<UndoActionResponse> results;

    @JsonProperty("message")
    private String message;
}
