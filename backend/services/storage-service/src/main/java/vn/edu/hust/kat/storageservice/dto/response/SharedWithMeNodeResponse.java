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
@Schema(description = "Thông tin file/folder được chia sẻ với người dùng hiện tại")
public class SharedWithMeNodeResponse {

    @JsonProperty("node_id")
    @Schema(description = "ID của file hoặc folder", example = "5")
    private Long nodeId;

    @JsonProperty("name")
    @Schema(description = "Tên của file hoặc folder", example = "Tai-lieu-du-an-2026")
    private String name;

    @JsonProperty("is_folder")
    @Schema(description = "true nếu là folder, false nếu là file", example = "true")
    private Boolean isFolder;

    @JsonProperty("mime_type")
    @Schema(description = "Định dạng MIME", example = "application/pdf")
    private String mimeType;

    @JsonProperty("size_bytes")
    @Schema(description = "Kích thước tính bằng byte", example = "0")
    private Long sizeBytes;

    @JsonProperty("permission")
    @Schema(description = "Quyền được cấp (VIEW hoặc EDIT)", example = "VIEW")
    private String permission;

    @JsonProperty("is_password_protected")
    @Schema(description = "true nếu có mật khẩu bảo vệ", example = "true")
    private Boolean isPasswordProtected;

    @JsonProperty("is_unlocked")
    @Schema(description = "true nếu đã mở khóa thành công trong phiên này", example = "false")
    private Boolean isUnlocked;

    @JsonProperty("owner")
    @Schema(description = "Thông tin người chia sẻ")
    private NodeOwnerResponse owner;

    @JsonProperty("shared_at")
    @Schema(description = "Thời điểm được chia sẻ")
    private LocalDateTime sharedAt;
}
