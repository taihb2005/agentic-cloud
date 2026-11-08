package vn.edu.hust.kat.storageservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodeShareDetailResponse {

    @JsonProperty("node_id")
    private Long nodeId;

    @JsonProperty("node_name")
    private String nodeName;

    @JsonProperty("is_folder")
    private Boolean isFolder;

    @JsonProperty("share_mode")
    private String shareMode;

    @JsonProperty("permission")
    private String permission;

    @JsonProperty("is_password_protected")
    private Boolean isPasswordProtected;

    @JsonProperty("shared_url")
    private String sharedUrl;

    @JsonProperty("share_token")
    private String shareToken;

    @JsonProperty("expires_at")
    private LocalDateTime expiresAt;

    @JsonProperty("max_access_count")
    private Integer maxAccessCount;

    @JsonProperty("access_count")
    private Integer accessCount;

    @JsonProperty("status")
    private String status;

    @Builder.Default
    @JsonProperty("users")
    private List<SharedUserItemResponse> users = new ArrayList<>();

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
