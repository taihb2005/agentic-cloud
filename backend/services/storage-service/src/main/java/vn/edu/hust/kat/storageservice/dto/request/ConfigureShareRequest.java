package vn.edu.hust.kat.storageservice.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigureShareRequest {

    @NotBlank(message = "share_mode không được để trống")
    @JsonProperty("share_mode")
    private String shareMode;

    @NotBlank(message = "permission không được để trống")
    @JsonProperty("permission")
    private String permission;

    @JsonProperty("user_ids")
    private List<UUID> userIds;

    @JsonProperty("password")
    private String password;

    @JsonProperty("expires_at")
    private LocalDateTime expiresAt;

    @JsonProperty("max_access_count")
    private Integer maxAccessCount;

    @JsonProperty("override_existing")
    private Boolean overrideExisting;
}
