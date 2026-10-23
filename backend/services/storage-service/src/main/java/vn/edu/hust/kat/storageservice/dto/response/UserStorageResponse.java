package vn.edu.hust.kat.storageservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStorageResponse {

    private UUID userId;
    private String storagePrefix;
    private String storageBucketName;
    private Long storageMaxQuotaBytes;
    private Long storageCurrentQuotaBytes;
    private Long rootNodeId;
    private String rootNodeName;
    private String rootNodePath;
    private String status;
    private LocalDateTime createdAt;
}
