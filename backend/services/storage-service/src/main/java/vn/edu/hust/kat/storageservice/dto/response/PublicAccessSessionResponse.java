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
@Schema(description = "Kết quả phiên làm việc mở khóa link chia sẻ công khai")
public class PublicAccessSessionResponse {

    @JsonProperty("public_access_token")
    @Schema(description = "Token phiên làm việc để gửi trong header X-Share-Access-Token", example = "pub_sess_7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c")
    private String publicAccessToken;

    @JsonProperty("token_type")
    @Builder.Default
    @Schema(description = "Loại token", example = "Bearer")
    private String tokenType = "Bearer";

    @JsonProperty("expires_in_seconds")
    @Schema(description = "Thời gian hiệu lực tính bằng giây", example = "7200")
    private Long expiresInSeconds;

    @JsonProperty("permission")
    @Schema(description = "Mức quyền của link công khai (VIEW hoặc EDIT)", example = "VIEW")
    private String permission;

    @JsonProperty("node")
    @Schema(description = "Thông tin node gốc được chia sẻ")
    private PublicShareNodeInfo node;
}
