package vn.edu.hust.kat.storageservice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin chủ sở hữu của node")
public class NodeOwnerResponse {

    @JsonProperty("user_id")
    @Schema(description = "UUID của chủ sở hữu", example = "7ac9e9d3-79a2-44ec-806d-7029af302116")
    private UUID userId;

    @JsonProperty("username")
    @Schema(description = "Username của chủ sở hữu", example = "alice")
    private String username;

    @JsonProperty("email")
    @Schema(description = "Email của chủ sở hữu", example = "alice@example.com")
    private String email;
}
