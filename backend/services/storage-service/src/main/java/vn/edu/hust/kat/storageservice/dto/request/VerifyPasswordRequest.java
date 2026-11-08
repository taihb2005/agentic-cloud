package vn.edu.hust.kat.storageservice.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu mở khóa node bằng mật khẩu")
public class VerifyPasswordRequest {

    @NotBlank(message = "Mật khẩu không được để trống")
    @JsonProperty("password")
    @Schema(description = "Mật khẩu cần xác thực", example = "MySuperSecretPassword@2026")
    private String password;
}
