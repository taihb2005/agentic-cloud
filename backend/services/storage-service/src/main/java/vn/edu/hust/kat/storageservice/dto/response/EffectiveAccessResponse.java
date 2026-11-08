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
@Schema(description = "Thông tin quyền hiệu dụng trên một node")
public class EffectiveAccessResponse {

    @JsonProperty("node_id")
    @Schema(description = "ID của node", example = "12")
    private Long nodeId;

    @JsonProperty("node_name")
    @Schema(description = "Tên file hoặc folder", example = "Ban-ve-CAD.dwg")
    private String nodeName;

    @JsonProperty("is_owner")
    @Schema(description = "true nếu người dùng hiện tại là Chủ sở hữu", example = "false")
    private Boolean isOwner;

    @JsonProperty("effective_permission")
    @Schema(description = "Mức quyền hiệu dụng (OWNER, VIEW, EDIT, hoặc NONE)", example = "VIEW")
    private String effectivePermission;

    @JsonProperty("is_inherited")
    @Schema(description = "true nếu quyền nhận được do kế thừa từ thư mục cha/tổ tiên", example = "true")
    private Boolean isInherited;

    @JsonProperty("inherited_from_node_id")
    @Schema(description = "ID của thư mục tổ tiên chia sẻ quyền gốc (nếu kế thừa)", example = "5")
    private Long inheritedFromNodeId;

    @JsonProperty("inherited_from_node_name")
    @Schema(description = "Tên của thư mục tổ tiên chia sẻ quyền gốc (nếu kế thừa)", example = "Tai-lieu-du-an-2026")
    private String inheritedFromNodeName;

    @JsonProperty("is_password_protected")
    @Schema(description = "true nếu node hoặc cây cha có mật khẩu", example = "true")
    private Boolean isPasswordProtected;

    @JsonProperty("is_unlocked")
    @Schema(description = "true nếu đã mở khóa thành công trong phiên này", example = "true")
    private Boolean isUnlocked;

    @JsonProperty("can_view")
    @Schema(description = "true nếu có quyền xem/duyệt/tải", example = "true")
    private Boolean canView;

    @JsonProperty("can_edit")
    @Schema(description = "true nếu có quyền sửa/tạo/upload", example = "false")
    private Boolean canEdit;
}
