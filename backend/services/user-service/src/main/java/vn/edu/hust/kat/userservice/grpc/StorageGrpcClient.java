package vn.edu.hust.kat.userservice.grpc;

import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.hust.kat.proto.storage.v1.GetUserStorageRequest;
import vn.edu.hust.kat.proto.storage.v1.GetUserStorageResponse;
import vn.edu.hust.kat.proto.storage.v1.StorageServiceGrpc;
import vn.edu.hust.kat.userservice.dto.response.UserStorageResponse;
import vn.edu.hust.kat.userservice.util.GrpcStatusRuntimeMapper;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class StorageGrpcClient {

    private final StorageServiceGrpc.StorageServiceBlockingStub storageStub;

    public UserStorageResponse getUserStorage(UUID userId) {

        var request = GetUserStorageRequest.newBuilder()
                .setUserId(userId.toString())
                .build();

        try {
            GetUserStorageResponse response = storageStub
                    .withDeadlineAfter(2, TimeUnit.SECONDS)
                    .getUserStorage(request);

            return mapToResponse(response);

        } catch (StatusRuntimeException ex) {
            log.error("Lỗi khi nhận response từ grpc getUserStorage: {}", ex.getMessage());

            throw GrpcStatusRuntimeMapper.throwFrom(ex);
        }
    }

    private UserStorageResponse mapToResponse(
            GetUserStorageResponse response
    ) {
        return UserStorageResponse.builder()
                .userId(UUID.fromString(response.getUserId()))
                .storagePrefix(response.getStoragePrefix())
                .storageBucketName(response.getStorageBucketName())
                .storageMaxQuotaBytes(response.getStorageMaxQuotaBytes())
                .storageCurrentQuotaBytes(response.getStorageCurrentQuotaBytes())
                .rootNodeId(response.getRootNodeId())
                .rootNodeName(response.getRootNodeName())
                .rootNodePath(response.getRootNodePath())
                .createdAt(LocalDateTime.parse(response.getCreatedAt()))
                .updatedAt(LocalDateTime.parse(response.getUpdatedAt()))
                .build();
    }

}