package vn.edu.hust.kat.apigateway.grpc;

import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import vn.edu.hust.kat.apigateway.util.GrpcStatusRuntimeMapper;
import vn.edu.hust.kat.proto.gateway.v1.ApiGatewayGrpc;
import vn.edu.hust.kat.proto.gateway.v1.FindUserRequest;
import vn.edu.hust.kat.proto.gateway.v1.FindUserResponse;
import vn.edu.hust.kat.proto.storage.v1.GetUserStorageResponse;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApiGatewayClient {
    private final ApiGatewayGrpc.ApiGatewayBlockingStub apiGatewayStub;

    public FindUserResponse checkIfUserExists(String userId) {
        FindUserRequest request = FindUserRequest.newBuilder()
                        .setUserId(userId)
                        .build();

        try {
            return apiGatewayStub
                    .withDeadlineAfter(2, TimeUnit.SECONDS)
                    .findUser(request);

        } catch (StatusRuntimeException ex) {
            log.error("Lỗi khi nhận response từ grpc getUserStorage: {}", ex.getMessage());

            throw GrpcStatusRuntimeMapper.throwFrom(ex);
        }
    }
}
