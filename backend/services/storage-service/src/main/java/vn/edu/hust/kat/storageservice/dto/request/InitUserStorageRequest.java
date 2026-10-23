package vn.edu.hust.kat.storageservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitUserStorageRequest {

    @NotNull(message = "userId must not be null")
    private UUID userId;

    private String username;

    private String email;

    private Long customQuotaBytes;
}
