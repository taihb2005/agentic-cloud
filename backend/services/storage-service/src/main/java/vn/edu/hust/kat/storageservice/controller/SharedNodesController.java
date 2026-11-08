package vn.edu.hust.kat.storageservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.hust.kat.common.api.response.ApiResponse;
import vn.edu.hust.kat.storageservice.dto.response.EffectiveAccessResponse;
import vn.edu.hust.kat.storageservice.dto.response.PagedResponse;
import vn.edu.hust.kat.storageservice.dto.response.SharedWithMeNodeResponse;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.service.SharedNodesService;

import java.util.UUID;

@RestController
@RequestMapping({"/storage-service/nodes", "/nodes"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Shared Nodes API", description = "Các API dành cho người dùng được chia sẻ (Shared Users)")
public class SharedNodesController {

    private final SharedNodesService sharedNodesService;

    @GetMapping("/shared-with-me")
    @Operation(summary = "Danh sách file/folder được người khác trực tiếp chia sẻ cho tôi")
    public ResponseEntity<ApiResponse<PagedResponse<SharedWithMeNodeResponse>>> getSharedWithMe(
            @AuthenticationPrincipal FsUser user,
            @RequestHeader(name = "X-User-Id", required = false) UUID headerUserId,
            @RequestHeader(name = "X-Share-Access-Token", required = false) String shareAccessToken,
            @Parameter(description = "Lọc theo loại node: FILE hoặc FOLDER")
            @RequestParam(name = "node_type", required = false) String nodeType,
            @Parameter(description = "Lọc theo quyền: VIEW hoặc EDIT")
            @RequestParam(name = "permission", required = false) String permission,
            @Parameter(description = "Tìm kiếm theo tên file/folder")
            @RequestParam(name = "name", required = false) String name,
            @Parameter(description = "Số trang (bắt đầu từ 0)")
            @RequestParam(name = "page", defaultValue = "0") int page,
            @Parameter(description = "Số lượng bản ghi mỗi trang")
            @RequestParam(name = "size", defaultValue = "20") int size,
            @Parameter(description = "Tiêu chí sắp xếp (mặc định: shared_at,desc)")
            @RequestParam(name = "sort", defaultValue = "shared_at,desc") String sort
    ) {
        UUID callerUserId = resolveUserId(user, headerUserId);
        PagedResponse<SharedWithMeNodeResponse> response = sharedNodesService.getSharedWithMe(
                callerUserId,
                nodeType,
                permission,
                name,
                page,
                size,
                sort,
                shareAccessToken
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách chia sẻ thành công", response));
    }

    @GetMapping({"/{id}/access", "/access/{id}"})
    @Operation(summary = "Kiểm tra quyền hiệu dụng trên một file hoặc folder")
    public ResponseEntity<ApiResponse<EffectiveAccessResponse>> checkEffectiveAccess(
            @AuthenticationPrincipal FsUser user,
            @RequestHeader(name = "X-User-Id", required = false) UUID headerUserId,
            @RequestHeader(name = "X-Share-Access-Token", required = false) String shareAccessToken,
            @Parameter(description = "ID của node cần kiểm tra quyền truy cập")
            @PathVariable("id") Long id
    ) {
        UUID callerUserId = resolveUserId(user, headerUserId);
        EffectiveAccessResponse response = sharedNodesService.checkEffectiveAccess(callerUserId, id, shareAccessToken);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private UUID resolveUserId(FsUser user, UUID headerUserId) {
        if (user != null && user.getId() != null) {
            return user.getId();
        }
        return headerUserId;
    }
}
