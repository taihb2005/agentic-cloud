package vn.edu.hust.kat.agentservice.util;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.common.api.exception.DuplicateResourceException;
import vn.edu.hust.kat.common.api.exception.ResourceNotFoundException;

public final class GrpcStatusRuntimeMapper {

    private GrpcStatusRuntimeMapper() {
    }

    public static RuntimeException throwFrom(StatusRuntimeException exception) {
        Status status = exception.getStatus();
        String description = status.getDescription() != null
                ? status.getDescription()
                : "Lỗi kết nối gRPC tới storage-service (" + status.getCode() + ")";

        return switch (status.getCode()) {
            case PERMISSION_DENIED -> new AccessDeniedException(description);
            case UNAUTHENTICATED -> new BadCredentialsException(description);
            case NOT_FOUND -> new ResourceNotFoundException(description);
            case ALREADY_EXISTS -> new DuplicateResourceException(description);
            case INVALID_ARGUMENT, FAILED_PRECONDITION -> new BadRequestException(description);
            default -> new RuntimeException(description, exception);
        };
    }
}
