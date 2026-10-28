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
import vn.edu.hust.kat.common.api.response.BatchOperationResponse;
import vn.edu.hust.kat.storageservice.dto.request.BatchNodesRequest;
import vn.edu.hust.kat.storageservice.dto.request.CreateFolderRequest;
import vn.edu.hust.kat.storageservice.dto.request.MoveNodeRequest;
import vn.edu.hust.kat.storageservice.dto.request.UpdateNodeRequest;
import vn.edu.hust.kat.storageservice.dto.response.*;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.service.FsNodeFolderService;
import vn.edu.hust.kat.storageservice.service.FsNodeService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/storage-service/nodes",})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Node API", description = "Các API quản lý node")
public class FsNodeController {

    private final FsNodeService fsNodeService;
    private final FsNodeFolderService fsFolderService;

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

    @PatchMapping("{id}")
    public ResponseEntity<ApiResponse<NodeResponse>> updateNode(
            @AuthenticationPrincipal FsUser user,
            @Valid @PathVariable String id,
            @Valid@RequestBody UpdateNodeRequest request
    ) {
        UUID userId = (user != null) ? user.getId() : null;

        log.info("Cập nhật thông tin node userId={}, node={}", userId, id);

        NodeResponse nodeResponse = fsNodeService.updateNode(userId, id, request);

        return ResponseEntity.ok()
                .body(ApiResponse.success("Cập nhật thành công", nodeResponse));
    }

    @DeleteMapping
    @Operation(summary = "Đưa danh sách node vào thùng rác (Xóa mềm)")
    public ResponseEntity<ApiResponse<BatchOperationResponse<DeleteResult, NodeFailureResult>>> softDeleteNodes(
            @AuthenticationPrincipal FsUser user,
            @Valid @RequestBody BatchNodesRequest request
    ) {
        UUID userId = (user != null) ? user.getId() : null;

        List<String> nodeIds = (request != null && request.getNodeIds() != null) ? request.getNodeIds() : List.of();
        log.info("Xóa mềm nodes: userId={}, nodeIds={}", userId, nodeIds);

        BatchOperationResponse<DeleteResult, NodeFailureResult> response = fsNodeService.softDeleteNodes(userId, nodeIds);
        String message = response.getFailedCount() == 0 ? "Xóa node hoàn tất" : "Xóa node hoàn tất, có node thất bại";

        return ResponseEntity.ok(ApiResponse.success(message, response));
    }

    @PostMapping("/move")
    @Operation(summary = "Di chuyển danh sách node sang thư mục đích")
    public ResponseEntity<ApiResponse<BatchOperationResponse<MoveResult, NodeFailureResult>>> moveNodes(
            @AuthenticationPrincipal FsUser user,
            @Valid @RequestBody MoveNodeRequest request
    ) {
        UUID userId = (user != null) ? user.getId() : null;

        log.info("Di chuyển nodes: userId={}, srcNodeIds={}, dstNodeId={}",
                userId, request != null ? request.getSrcNodeIds() : null, request != null ? request.getDstNodeId() : null);

        BatchOperationResponse<MoveResult, NodeFailureResult> response = fsNodeService.moveNodes(userId, request);
        String message = response.getFailedCount() == 0 ? "Di chuyển node hoàn tất" : "Di chuyển node hoàn tất, có node thất bại";

        return ResponseEntity.ok(ApiResponse.success(message, response));
    }
}
