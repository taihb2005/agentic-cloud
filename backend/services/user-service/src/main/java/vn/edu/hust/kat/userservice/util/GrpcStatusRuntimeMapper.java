package vn.edu.hust.kat.userservice.util;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import vn.edu.hust.kat.common.api.exception.ResourceNotFoundException;

public class GrpcStatusRuntimeMapper {
    public static RuntimeException throwFrom(StatusRuntimeException exception){
        Status status = exception.getStatus();

        switch (status.getCode()) {
            case PERMISSION_DENIED -> {
                return new AccessDeniedException(status.getDescription());
            }

            case UNAUTHENTICATED -> {
                return new BadCredentialsException(status.getDescription());
            }

            case NOT_FOUND -> {
                return new ResourceNotFoundException(status.getDescription());
            }

            default -> {
                return new RuntimeException(status.getDescription());
            }
        }
    }
}
