package vn.edu.hust.kat.storageservice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.edu.hust.kat.storageservice.entity.FsNode;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin node gốc được chia sẻ công khai")
public class PublicShareNodeInfo {

    @JsonProperty("id")
    @Schema(description = "ID của file hoặc folder", example = "8")
    private Long id;

    @JsonProperty("name")
    @Schema(description = "Tên của file hoặc folder", example = "hop-dong-lao-dong.pdf")
    private String name;

    @JsonProperty("is_folder")
    @Schema(description = "true nếu là thư mục, false nếu là file", example = "false")
    private Boolean isFolder;

    @JsonProperty("size_bytes")
    @Schema(description = "Kích thước tính theo byte", example = "1048576")
    private Long sizeBytes;

    @JsonProperty("mime_type")
    @Schema(description = "Định dạng MIME của file", example = "application/pdf")
    private String mimeType;

    public static PublicShareNodeInfo from(FsNode node) {
        if (node == null) return null;
        return PublicShareNodeInfo.builder()
                .id(node.getId())
                .name(node.getName())
                .isFolder(node.getIsFolder())
                .sizeBytes(node.getSizeBytes() != null ? node.getSizeBytes() : 0L)
                .mimeType(node.getMimeType())
                .build();
    }
}
