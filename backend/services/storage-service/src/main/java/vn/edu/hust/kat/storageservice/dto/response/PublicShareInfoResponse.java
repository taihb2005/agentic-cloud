package vn.edu.hust.kat.storageservice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin cơ bản về liên kết chia sẻ công khai")
public class PublicShareInfoResponse {

    @JsonProperty("share_token")
    @Schema(description = "Mã token của link chia sẻ", example = "a8f9c1d2e3b4a5c6d7e8f9a0b1c2d3e4")
    private String shareToken;

    @JsonProperty("node_name")
    @Schema(description = "Tên tệp tin hoặc thư mục", example = "hop-dong-lao-dong.pdf")
    private String nodeName;

    @JsonProperty("is_folder")
    @Schema(description = "true nếu là thư mục, false nếu là file", example = "false")
    private Boolean isFolder;

    @JsonProperty("mime_type")
    @Schema(description = "Định dạng MIME của tệp tin", example = "application/pdf")
    private String mimeType;

    @JsonProperty("size_bytes")
    @Schema(description = "Kích thước tính bằng bytes", example = "1048576")
    private Long sizeBytes;

    @JsonProperty("permission")
    @Schema(description = "Quyền được cấp trên link (VIEW hoặc EDIT)", example = "VIEW")
    private String permission;

    @JsonProperty("requires_password")
    @Schema(description = "Cho biết link có yêu cầu mật khẩu hay không", example = "true")
    private Boolean requiresPassword;

    @JsonProperty("expires_at")
    @Schema(description = "Thời điểm hết hạn chia sẻ")
    private LocalDateTime expiresAt;

    @JsonProperty("owner_display_name")
    @Schema(description = "Tên hiển thị hoặc tên người sở hữu", example = "Alice")
    private String ownerDisplayName;
}
