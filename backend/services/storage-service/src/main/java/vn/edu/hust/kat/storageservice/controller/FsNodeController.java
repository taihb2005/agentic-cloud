package vn.edu.hust.kat.storageservice.controller;

import io.minio.messages.RestoreRequest;
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
import vn.edu.hust.kat.storageservice.dto.request.CreateFolderRequest;
import vn.edu.hust.kat.storageservice.dto.request.MoveNodeRequest;
import vn.edu.hust.kat.storageservice.dto.response.NodeResponse;
import vn.edu.hust.kat.storageservice.dto.response.PagedResponse;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.service.FsFolderService;
import vn.edu.hust.kat.storageservice.service.FsNodeService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({
        "/storage-service/nodes",
        "/nodes",
        "/api/v1/storage-service/nodes"
})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Node API", description = "Các API quản lý node")
public class FsNodeController {

    private final FsNodeService fsNodeService;
    private final FsFolderService fsFolderService;

    @GetMapping
    @Operation(summary = "Lấy danh sách file/folder con (Duyệt thư mục)")
    public ResponseEntity<ApiResponse<PagedResponse<NodeResponse>>> getChildNodes(
            @AuthenticationPrincipal FsUser user,

            @Parameter(description = "ID của thư mục cha (null/bỏ trống để lấy thư mục gốc)")
            @RequestParam(value = "parent_id", required = false) String parentId,

            @Parameter(description = "Bộ lọc loại node (FILE hoặc FOLDER)")
            @RequestParam(value = "node_type", required = false) String nodeType,

            @Parameter(description = "Bộ lọc tên thư mục hoặc file trong thư mục hiện tại")
            @RequestParam(value = "name", required = false) String name,

            @Parameter(description = "Bộ lọc loại file")
            @RequestParam(value = "mime_type", required = false) String mimeType,

            @Parameter(description = "Trang hiện tại (bắt đầu từ 0)")
            @RequestParam(value = "page", defaultValue = "0") int page,

            @Parameter(description = "Số lượng bản ghi mỗi trang")
            @RequestParam(value = "size", defaultValue = "20") int size,

            @Parameter(description = "Tiêu chí sắp xếp, ví dụ: name,asc")
            @RequestParam(value = "sort", defaultValue = "name,asc") List<String> sorts
    ) {
        UUID userId = (user != null) ? user.getId() : null;

        log.info("Lấy danh sách file/folder con: userId={}, parentId={}, nodeType={}, name={}, mimeType={}, page={}, size={}, sort={}",
                userId, parentId, nodeType, name, mimeType, page, size, sorts.toString());

        PagedResponse<NodeResponse> response = fsNodeService.getChildNodes(
                userId,
                parentId,
                nodeType,
                name,
                mimeType,
                page,
                size,
                sorts
        );

        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách thành công", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NodeResponse>> getFolder(
            @AuthenticationPrincipal FsUser user,
            @PathVariable String id
    ) {
        UUID userId = (user != null) ? user.getId() : null;

        log.info("Lấy metadata userId={}, node={}", userId, id);

        NodeResponse nodeResponse = fsNodeService.getNodeMetadata(userId, id);

        return ResponseEntity.ok()
                .body(ApiResponse.success("Lấy metadata thành công", nodeResponse));
    }

    @PostMapping("/folders")
    public ResponseEntity<ApiResponse<NodeResponse>> createFolder(
            @AuthenticationPrincipal FsUser user,
            @RequestBody CreateFolderRequest request
    ) {
        UUID userId = (user != null) ? user.getId() : null;

        log.info("Tạo thư mục con cho userId={}, parentId={}, name={}", userId, request.getParentId(), request.getName());

        NodeResponse nodeResponse = fsFolderService.createFolder(userId, request.getParentId(), request.getName());

        return ResponseEntity.ok()
                .body(ApiResponse.success("Tạo thư mục thành công", nodeResponse));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Đưa node vào thùng rác")
    public ResponseEntity<ApiResponse<Void>> softDeleteNode(
            @AuthenticationPrincipal FsUser user,
            @PathVariable String id
    ) {
        UUID userId = (user != null) ? user.getId() : null;

        log.info("Xóa mềm node: userId={}, nodeId={}", userId, id);

        fsNodeService.softDeleteNode(userId, id);

        return ResponseEntity.ok(ApiResponse.success("Đã chuyển vào thùng rác thành công", null));
    }

    @PostMapping("/move")
    @Operation(summary = "Di chuyển node")
    public ResponseEntity<ApiResponse<NodeResponse>> restoreNode(
            @AuthenticationPrincipal FsUser user,
            @Valid @RequestBody MoveNodeRequest request
    ) {
        UUID userId = (user != null) ? user.getId() : null;

        log.info("Di chuyển node: userId={}, srcNodeId={}, dstNodeId={}", userId, request.getSrcNodeId(), request.getDstNodeId());

        NodeResponse nodeResponse = fsNodeService.moveNode(userId, request.getSrcNodeId(), true, request.getDstNodeId(), true);

        return ResponseEntity.ok(ApiResponse.success("Di chuyển thanh công", nodeResponse));
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "Khôi phục Node đã xóa từ thùng rác")
    public ResponseEntity<ApiResponse<NodeResponse>> restoreNode(
            @AuthenticationPrincipal FsUser user,
            @PathVariable String id
    ) {
        UUID userId = (user != null) ? user.getId() : null;

        log.info("Khôi phục node: userId={}, nodeId={}", userId, id);

        NodeResponse nodeResponse = fsNodeService.restoreNode(userId, id);

        return ResponseEntity.ok(ApiResponse.success("Khôi phục thành công", nodeResponse));
    }
}
