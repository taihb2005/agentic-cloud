package vn.edu.hust.kat.storageservice.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.common.api.exception.ResourceNotFoundException;
import vn.edu.hust.kat.proto.storage.v1.GetUserStorageRequest;
import vn.edu.hust.kat.proto.storage.v1.GetUserStorageResponse;
import vn.edu.hust.kat.proto.storage.v1.StorageServiceGrpc;
import vn.edu.hust.kat.storageservice.dto.response.UserStorageResponse;
import vn.edu.hust.kat.storageservice.service.StorageUserService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserStorageGrpcService extends StorageServiceGrpc.StorageServiceImplBase {

    private final StorageUserService storageUserService;

    @Override
    public void getUserStorage(
            GetUserStorageRequest request,
            StreamObserver<GetUserStorageResponse> responseObserver
    ) {
        try {
            UUID userId = UUID.fromString(request.getUserId());
            UserStorageResponse uResponse = storageUserService.getUserStorage(userId);

            GetUserStorageResponse getResponse = GetUserStorageResponse.newBuilder()
                            .setUserId(uResponse.getUserId().toString())
                            .setStoragePrefix(uResponse.getStoragePrefix())
                            .setStorageBucketName(uResponse.getStorageBucketName())
                            .setStorageMaxQuotaBytes(uResponse.getStorageMaxQuotaBytes())
                            .setStorageCurrentQuotaBytes(uResponse.getStorageCurrentQuotaBytes())
                            .setRootNodeId(uResponse.getRootNodeId())
                            .setRootNodeName(uResponse.getRootNodeName())
                            .setRootNodePath(uResponse.getRootNodePath())
                            .setCreatedAt(uResponse.getCreatedAt().toString())
                            .setUpdatedAt(uResponse.getUpdatedAt().toString())
                            .build();


            responseObserver.onNext(getResponse);
            responseObserver.onCompleted();

        } catch (ResourceNotFoundException e) {
            responseObserver.onError(
                    Status.NOT_FOUND
                            .withDescription(e.getMessage())
                            .asRuntimeException()
            );
        } catch (BadRequestException e) {
            responseObserver.onError(
                    Status.FAILED_PRECONDITION
                            .withDescription(e.getMessage())
                            .asRuntimeException()
            );
        } catch (Exception e) {
            responseObserver.onError(
                    Status.INTERNAL
                            .asRuntimeException()
            );
        }
    }

}
