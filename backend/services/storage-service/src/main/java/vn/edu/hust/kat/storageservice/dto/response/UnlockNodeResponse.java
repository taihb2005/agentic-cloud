package vn.edu.hust.kat.storageservice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin mở khóa node bằng mật khẩu")
public class UnlockNodeResponse {

    @JsonProperty("node_id")
    @Schema(description = "ID của file hoặc folder vừa mở khóa", example = "5")
    private Long nodeId;

    @JsonProperty("share_access_token")
    @Schema(description = "Token phiên làm việc để gửi trong header X-Share-Access-Token")
    private String shareAccessToken;

    @JsonProperty("token_type")
    @Builder.Default
    @Schema(description = "Loại token", example = "Bearer")
    private String tokenType = "Bearer";

    @JsonProperty("expires_in_seconds")
    @Schema(description = "Thời gian hiệu lực tính bằng giây", example = "7200")
    private Long expiresInSeconds;

    @JsonProperty("effective_permission")
    @Schema(description = "Mức quyền hiệu dụng (VIEW hoặc EDIT)", example = "VIEW")
    private String effectivePermission;

    @JsonProperty("applies_to_descendants")
    @Builder.Default
    @Schema(description = "Mở khóa cho cả cây con bên dưới", example = "true")
    private Boolean appliesToDescendants = true;
}
