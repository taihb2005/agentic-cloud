package vn.edu.hust.kat.storageservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.common.api.exception.ResourceNotFoundException;
import vn.edu.hust.kat.common.api.response.ApiResponse;
import vn.edu.hust.kat.storageservice.controller.FsNodeAccessController;
import vn.edu.hust.kat.storageservice.controller.FsNodePasswordController;
import vn.edu.hust.kat.storageservice.controller.SharedNodesController;

@RestControllerAdvice(assignableTypes = {
        FsNodeAccessController.class,
        FsNodePasswordController.class,
        SharedNodesController.class
})
@Slf4j
public class SharedNodesExceptionHandler {

    @ExceptionHandler(InvalidSharePasswordException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidSharePassword(InvalidSharePasswordException ex) {
        log.warn("Invalid share password: {}", ex.getMessage());
        ApiResponse<Void> body = ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .error(ex.getMessage())
                .code(HttpStatus.UNAUTHORIZED.value())
                .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        ApiResponse<Void> body = ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .error(ex.getMessage())
                .code(HttpStatus.FORBIDDEN.value())
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        ApiResponse<Void> body = ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .error(ex.getMessage())
                .code(HttpStatus.NOT_FOUND.value())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        ApiResponse<Void> body = ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .error(ex.getMessage())
                .code(HttpStatus.BAD_REQUEST.value())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
