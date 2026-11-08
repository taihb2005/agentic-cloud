package vn.edu.hust.kat.storageservice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodePasswordStatusResponse {

    @JsonProperty("node_id")
    private Long nodeId;

    @JsonProperty("is_password_protected")
    private boolean isPasswordProtected;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
