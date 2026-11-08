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
import vn.edu.hust.kat.storageservice.dto.request.ConfigureShareRequest;
import vn.edu.hust.kat.storageservice.dto.request.UpdateUserGroupShareRequest;
import vn.edu.hust.kat.storageservice.dto.response.NodeShareDetailResponse;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.service.FsNodeShareService;

import java.util.UUID;

@RestController
@RequestMapping({"/storage-service/nodes"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Node Share API", description = "Các API quản lý chia sẻ file/folder dành cho Chủ sở hữu (Owner)")
public class FsNodeShareController {

    private final FsNodeShareService fsNodeShareService;

    @PostMapping({"/{id}/share", "/share/{id}"})
    @Operation(summary = "Thiết lập chia sẻ file hoặc folder (Chỉ Chủ sở hữu)")
    public ResponseEntity<ApiResponse<NodeShareDetailResponse>> createOrUpdateShare(
            @AuthenticationPrincipal FsUser user,
            @Parameter(description = "ID của file hoặc folder cần chia sẻ")
            @PathVariable("id") Long id,
            @Valid @RequestBody ConfigureShareRequest request
    ) {
        UUID callerUserId = (user != null) ? user.getId() : null;
        NodeShareDetailResponse response = fsNodeShareService.configureShare(callerUserId, id, request);
        return ResponseEntity.ok(ApiResponse.success("Thiết lập chia sẻ thành công", response));
    }

    @GetMapping({"/{id}/share", "/share/{id}"})
    @Operation(summary = "Xem chi tiết cấu hình chia sẻ hiện tại (Chỉ Chủ sở hữu)")
    public ResponseEntity<ApiResponse<NodeShareDetailResponse>> getShareDetail(
            @AuthenticationPrincipal FsUser user,
            @Parameter(description = "ID của file hoặc folder")
            @PathVariable("id") Long id
    ) {
        UUID callerUserId = (user != null) ? user.getId() : null;
        NodeShareDetailResponse response = fsNodeShareService.getShareDetail(callerUserId, id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping({"/{id}/share/users", "/share/{id}/users"})
    @Operation(summary = "Thêm/xóa user hoặc đổi mức quyền nhóm user trong mode USER_GROUP (Chỉ Chủ sở hữu)")
    public ResponseEntity<ApiResponse<NodeShareDetailResponse>> updateUserGroupShare(
            @AuthenticationPrincipal FsUser user,
            @Parameter(description = "ID của file hoặc folder")
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateUserGroupShareRequest request
    ) {
        UUID callerUserId = (user != null) ? user.getId() : null;
        NodeShareDetailResponse response = fsNodeShareService.updateUserGroupShare(callerUserId, id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật danh sách chia sẻ thành công", response));
    }

    @DeleteMapping({"/{id}/share/users/{userId}", "/share/{id}/users/{userId}"})
    @Operation(summary = "Thu hồi quyền truy cập của một user cụ thể trong nhóm (Chỉ Chủ sở hữu)")
    public ResponseEntity<ApiResponse<Void>> revokeUserShare(
            @AuthenticationPrincipal FsUser user,
            @Parameter(description = "ID của file hoặc folder")
            @PathVariable("id") Long id,
            @Parameter(description = "UUID của user cần thu hồi quyền")
            @PathVariable("userId") UUID userId
    ) {
        UUID callerUserId = (user != null) ? user.getId() : null;
        fsNodeShareService.revokeUserShare(callerUserId, id, userId);
        return ResponseEntity.ok(ApiResponse.success("Đã thu hồi quyền truy cập của người dùng thành công", null));
    }

    @DeleteMapping({"/{id}/share", "/share/{id}"})
    @Operation(summary = "Hủy toàn bộ chia sẻ trên node, đưa về chế độ riêng tư PRIVATE (Chỉ Chủ sở hữu)")
    public ResponseEntity<ApiResponse<Void>> revokeAllShare(
            @AuthenticationPrincipal FsUser user,
            @Parameter(description = "ID của file hoặc folder")
            @PathVariable("id") Long id
    ) {
        UUID callerUserId = (user != null) ? user.getId() : null;
        fsNodeShareService.revokeAllShare(callerUserId, id);
        return ResponseEntity.ok(ApiResponse.success("Đã hủy bỏ toàn bộ chia sẻ. Node hiện ở chế độ riêng tư (PRIVATE)", null));
    }
}
