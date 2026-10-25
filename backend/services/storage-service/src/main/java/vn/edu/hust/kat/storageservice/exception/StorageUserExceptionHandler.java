package vn.edu.hust.kat.storageservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.edu.hust.kat.common.api.response.ApiResponse;

@RestControllerAdvice(assignableTypes = StorageUserExceptionHandler.class)
@Slf4j
public class StorageUserExceptionHandler {
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(
            MissingRequestHeaderException ex
    ) {
        return ResponseEntity.ok()
                .body(ApiResponse.error("Thiếu header được yêu cầu", HttpStatus.BAD_REQUEST));
    }
}
