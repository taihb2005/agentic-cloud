package vn.edu.hust.kat.storageservice.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoveNodeRequest {
    @JsonProperty("src_node_id")
    private String srcNodeId;

    @JsonProperty("dst_node_id")
    private String dstNodeId;
}
