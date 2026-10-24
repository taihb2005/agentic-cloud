package vn.edu.hust.kat.storageservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.common.api.response.ApiResponse;
import vn.edu.hust.kat.storageservice.dto.request.InitUserStorageRequest;
import vn.edu.hust.kat.storageservice.dto.response.UserQuotaResponse;
import vn.edu.hust.kat.storageservice.dto.response.UserStorageResponse;
import vn.edu.hust.kat.storageservice.service.QuotaService;
import vn.edu.hust.kat.storageservice.service.StorageUserService;

import java.util.UUID;

@RestController
@RequestMapping({
        "/storage-service/storage/users",
        "/storage/users",
        "/api/v1/storage-service/storage/users"
})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Storage User & Quota API", description = "Các API quản lý không gian lưu trữ và quota của người dùng")
public class StorageUserController {

    private final StorageUserService storageUserService;
    private final QuotaService quotaService;

    @GetMapping("/quota/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    @Operation(summary = "Kiểm tra dung lượng lưu trữ của người dùng hiện tại")
    public ResponseEntity<ApiResponse<UserQuotaResponse>> getUserQuota(
            @PathVariable UUID id
    ) {
        log.info("Nhận yêu cầu lấy thông tin quota của người dùng có id: {}", id);

        UserQuotaResponse response = quotaService.getUserQuota(id);

        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin thành công", response));
    }

    @GetMapping("/storage/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    @Operation(summary = "Lấy thông tin không gian lưu trữ của người dùng")
    public ResponseEntity<ApiResponse<UserStorageResponse>> getUserStorage(
            @PathVariable UUID id
    ) {
        log.info("Nhận yêu cầu lấy thông tin người dùng của người dùng có id: {}", id);

        UserStorageResponse response = storageUserService.getUserStorage(id);

        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin thành công", response));
    }

    @PostMapping("/init")
    @Operation(summary = "Khởi tạo không gian lưu trữ và cây thư mục gốc cho người dùng mới")
    public ResponseEntity<ApiResponse<UserStorageResponse>> initUserStorage(
            @Valid @RequestBody InitUserStorageRequest request
    ) {
        log.info("Bắt đầu khởi tạo storage cho userId: {}", request.getUserId());
        UserStorageResponse response = storageUserService.initUserStorage(request);

        HttpStatus status = storageUserService.isUserNew(request.getUserId()) ? HttpStatus.CREATED : HttpStatus.OK;

        String message = storageUserService.isUserNew(request.getUserId())
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
