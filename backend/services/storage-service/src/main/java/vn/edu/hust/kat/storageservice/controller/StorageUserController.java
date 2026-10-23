package vn.edu.hust.kat.storageservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.hust.kat.common.api.response.ApiResponse;
import vn.edu.hust.kat.storageservice.dto.request.InitUserStorageRequest;
import vn.edu.hust.kat.storageservice.dto.response.UserStorageResponse;
import vn.edu.hust.kat.storageservice.service.StorageUserService;

import java.util.UUID;

@RestController
@RequestMapping("/storage-service/storage/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Internal Storage API", description = "Các API nội bộ phục vụ giao tiếp giữa các microservices")
public class StorageUserController {

    private final StorageUserService storageUserService;

    @PostMapping("/init")
    @Operation(summary = "Khởi tạo không gian lưu trữ và cây thư mục gốc cho người dùng mới")
    public ResponseEntity<ApiResponse<UserStorageResponse>> initUserStorage(
            @Valid @RequestBody InitUserStorageRequest request
    ) {
        log.info("Nhận yêu cầu API nội bộ khởi tạo storage cho userId: {}", request.getUserId());
        UserStorageResponse response = storageUserService.initUserStorage(request);

        HttpStatus status = "INITIALIZED".equals(response.getStatus()) ? HttpStatus.CREATED : HttpStatus.OK;

        String message = "INITIALIZED".equals(response.getStatus())
                ? "Khởi tạo không gian lưu trữ và cây dữ liệu thành công"
                : "Không gian lưu trữ của người dùng đã tồn tại";

        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(message, response, status));
    }

    @PostMapping("/{userId}/init")
    @Operation(summary = "Khởi tạo nhanh storage cho user id qua path variable")
    public ResponseEntity<ApiResponse<UserStorageResponse>> initUserStorageByUserId(
            @PathVariable UUID userId
    ) {
        InitUserStorageRequest request = InitUserStorageRequest.builder()
                .userId(userId)
                .build();

        return initUserStorage(request);
    }
}
