package vn.edu.hust.kat.storageservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadUrlRequest {

    @NotEmpty(message = "Danh sách files không được để trống")
    @Valid
    private List<FileUploadItemRequest> files;
}
