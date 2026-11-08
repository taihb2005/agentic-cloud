package vn.edu.hust.kat.storageservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.edu.hust.kat.common.api.exception.DuplicateResourceException;
import vn.edu.hust.kat.common.api.response.ApiResponse;
import vn.edu.hust.kat.storageservice.controller.FsNodeShareController;

import java.util.Map;

@RestControllerAdvice(assignableTypes = FsNodeShareController.class)
@Slf4j
public class NodeShareExceptionHandler {
    @ExceptionHandler
    public ResponseEntity<ApiResponse<Map<String, String>>> handleDuplicateShareMode(
            DuplicateResourceException ex) {

        return ResponseEntity.ok()
                .body(ApiResponse.error(ex.getMessage(), HttpStatus.CONFLICT));
    }

    @ExceptionHandler
    public ResponseEntity<ApiResponse<Map<String, String>>> handleAccessDeniedException(
            AccessDeniedException ex) {
        return ResponseEntity.ok()
                .body(ApiResponse.error(ex.getMessage(), HttpStatus.FORBIDDEN));
    }
}
