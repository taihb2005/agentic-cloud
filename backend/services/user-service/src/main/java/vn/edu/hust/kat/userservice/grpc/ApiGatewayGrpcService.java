package vn.edu.hust.kat.userservice.grpc;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edu.hust.kat.proto.gateway.v1.ApiGatewayGrpc;
import vn.edu.hust.kat.proto.gateway.v1.FindUserRequest;
import vn.edu.hust.kat.proto.gateway.v1.FindUserResponse;
import vn.edu.hust.kat.userservice.entity.User;
import vn.edu.hust.kat.userservice.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiGatewayGrpcService extends ApiGatewayGrpc.ApiGatewayImplBase {

    private final UserRepository userRepository;

    @Override
    public void findUser(
            FindUserRequest request,
            StreamObserver<FindUserResponse> responseObserver
    ) {
        try {
            UUID userId = UUID.fromString(request.getUserId());
            Optional<User> user = userRepository.findById(userId);

            FindUserResponse fResponse;
            fResponse = user.map(value -> FindUserResponse.newBuilder()
                    .setUserId(value.getUserId())
                    .setExists(Boolean.TRUE)
                    .build()).orElseGet(() -> FindUserResponse.newBuilder()
                    .setUserId("")
                    .setExists(Boolean.FALSE)
                    .build());

            responseObserver.onNext(fResponse);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Kiểm tra sự tồn tại của user thất bại: {}", e.getMessage());
        }
    }
}
