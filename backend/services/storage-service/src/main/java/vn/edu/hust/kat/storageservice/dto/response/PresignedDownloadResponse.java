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
@Schema(description = "Presigned URL để tải file trực tiếp từ MinIO/S3")
public class PresignedDownloadResponse {

    @JsonProperty("node_id")
    @Schema(description = "ID của file", example = "8")
    private Long nodeId;

    @JsonProperty("file_name")
    @Schema(description = "Tên của file", example = "hop-dong-lao-dong.pdf")
    private String fileName;

    @JsonProperty("download_url")
    @Schema(description = "Presigned URL tải trực tiếp", example = "http://localhost:9000/agentic-cloud/users/alice/hop-dong-lao-dong.pdf?X-Amz-Algorithm=...")
    private String downloadUrl;

    @JsonProperty("expires_in_seconds")
    @Schema(description = "Thời gian hết hạn tính bằng giây", example = "900")
    private Integer expiresInSeconds;
}
