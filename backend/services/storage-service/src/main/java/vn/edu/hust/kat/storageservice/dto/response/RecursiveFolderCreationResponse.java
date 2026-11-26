package vn.edu.hust.kat.storageservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.ALWAYS)
public class RecursiveFolderCreationResponse {

    @JsonProperty("leaf_node")
    private NodeResponse leafNode;

    @JsonProperty("first_created_node_id")
    private Long firstCreatedNodeId;

    @JsonProperty("created_count")
    private int createdCount;

    @JsonProperty("reused_count")
    private int reusedCount;

    @JsonProperty("created_nodes")
    private List<NodeResponse> createdNodes;
}
