package vn.edu.hust.kat.storageservice.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
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
public class MoveNodeRequest {

    @JsonProperty("src_node_ids")
    @JsonAlias({"src_node_id", "srcNodeIds", "srcNodeId"})
    private List<String> srcNodeIds;

    @JsonProperty("dst_node_id")
    @JsonAlias({"dstNodeId"})
    private String dstNodeId;
}
