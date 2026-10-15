package vn.edu.hust.kat.common.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.time.Instant;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private Integer code;
    private T data;
    private String error;

    @Builder.Default
    private Instant timestamp = Instant.now();

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .code(200)
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .code(200)
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data, HttpStatus code) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .code(code.value())
                .build();
    }

    public static <T> ApiResponse<T> error(String error, HttpStatus code) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(error)
                .code(code.value())
                .build();
    }

    public static <T> ApiResponse<T> message(String message) {
        return ApiResponse.<T>builder()
            .success(true)
            .message(message)
            .build();
    }
}
