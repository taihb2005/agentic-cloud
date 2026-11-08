package vn.edu.hust.kat.storageservice.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserGroupShareRequest {

    @NotBlank(message = "action không được để trống (ADD, REMOVE, CHANGE_PERMISSION, REPLACE_ALL)")
    @JsonProperty("action")
    private String action;

    @JsonProperty("user_ids")
    private List<UUID> userIds;

    @JsonProperty("permission")
    private String permission;
}
