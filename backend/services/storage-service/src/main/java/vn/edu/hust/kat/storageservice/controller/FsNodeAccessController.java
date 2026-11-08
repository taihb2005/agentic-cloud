package vn.edu.hust.kat.storageservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.hust.kat.common.api.response.ApiResponse;
import vn.edu.hust.kat.storageservice.dto.request.VerifyPasswordRequest;
import vn.edu.hust.kat.storageservice.dto.response.UnlockNodeResponse;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.service.FsNodeAccessService;

import java.util.UUID;

@RestController
@RequestMapping({"/storage-service/nodes", "/nodes"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Node Access API", description = "Các API xác thực mật khẩu truy cập node (Password Verification)")
public class FsNodeAccessController {

    private final FsNodeAccessService fsNodeAccessService;

    @PostMapping({"/{id}/verify-password", "/verify-password/{id}"})
    @Operation(summary = "Mở khóa Node bằng Mật khẩu dành cho người dùng được chia sẻ")
    public ResponseEntity<ApiResponse<UnlockNodeResponse>> verifyNodePassword(
            @AuthenticationPrincipal FsUser user,
            @RequestHeader(name = "X-User-Id", required = false) UUID headerUserId,
            @Parameter(description = "ID của file hoặc folder cần mở khóa")
            @PathVariable("id") Long id,
            @Valid @RequestBody VerifyPasswordRequest request
    ) {
        UUID callerUserId = resolveUserId(user, headerUserId);
        UnlockNodeResponse response = fsNodeAccessService.verifyNodePassword(callerUserId, id, request);
        return ResponseEntity.ok(ApiResponse.success("Xác thực mật khẩu thành công. Đã mở khóa quyền truy cập", response));
    }

    private UUID resolveUserId(FsUser user, UUID headerUserId) {
        if (user != null && user.getId() != null) {
            return user.getId();
        }
        return headerUserId;
    }
}
