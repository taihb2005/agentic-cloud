package vn.edu.hust.kat.storageservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu mở khóa truy cập link chia sẻ công khai")
public class PublicAccessRequest {

    @Schema(description = "Mật khẩu bảo vệ (nếu liên kết có đặt mật khẩu)", example = "Secret123")
    private String password;
}
