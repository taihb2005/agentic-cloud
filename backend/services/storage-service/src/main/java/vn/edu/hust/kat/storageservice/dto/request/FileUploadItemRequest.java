package vn.edu.hust.kat.storageservice.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadItemRequest {

    @JsonProperty("parent_id")
    @JsonAlias({"parentId"})
    private String parentId;

    @NotBlank(message = "Tên file không được để trống")
    @JsonProperty("name")
    private String name;

    @NotNull(message = "Kích thước file không được để trống")
    @PositiveOrZero(message = "Kích thước file phải lớn hơn hoặc bằng 0")
    @JsonProperty("size")
    private Long size;

    @JsonProperty("mime_type")
    @JsonAlias({"mimeType"})
    private String mimeType;

    @NotBlank(message = "Hash file không được để trống")
    @JsonProperty("hash")
    @JsonAlias({"file_hash", "fileHash"})
    private String hash;
}
