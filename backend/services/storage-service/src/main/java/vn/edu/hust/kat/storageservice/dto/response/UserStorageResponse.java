package vn.edu.hust.kat.storageservice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStorageResponse {

    @JsonProperty("user_id")
    private UUID userId;

    @JsonProperty("storage_prefix")
    private String storagePrefix;

    @JsonProperty("storage_bucket_name")
    private String storageBucketName;

    @JsonProperty("storage_max_quota_bytes")
    private Long storageMaxQuotaBytes;

    @JsonProperty("storage_current_quota_bytes")
    private Long storageCurrentQuotaBytes;

    @JsonProperty("root_node_id")
    private Long rootNodeId;

    @JsonProperty("root_node_name")
    private String rootNodeName;

    @JsonProperty("root_node_path")
    private String rootNodePath;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
