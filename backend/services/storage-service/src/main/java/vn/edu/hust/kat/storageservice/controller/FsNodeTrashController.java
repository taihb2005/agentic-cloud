package vn.edu.hust.kat.storageservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.jdbc.batch.spi.Batch;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.hust.kat.common.api.response.ApiResponse;
import vn.edu.hust.kat.common.api.response.BatchOperationResponse;
import vn.edu.hust.kat.common.api.response.OperationResponse;
import vn.edu.hust.kat.storageservice.config.NodeDeleteProperties;
import vn.edu.hust.kat.storageservice.dto.request.BatchNodesRequest;
import vn.edu.hust.kat.storageservice.dto.response.*;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.service.FsNodeTrashService;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/storage-service/nodes/trash"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Node Trash API", description = "Các API quản lý node trong thùng rác")
public class FsNodeTrashController {

    private final FsNodeTrashService fsNodeTrashService;
    private final NodeDeleteProperties nodeDeleteProperties;

    @GetMapping
    @Operation(summary = "Lấy danh sách node trong thùng rác")
    public ResponseEntity<ApiResponse<PagedResponse<NodeResponse>>> getTrash(
            @AuthenticationPrincipal FsUser user,

            @Parameter(description = "ID của thư mục cha")
            @RequestParam(value = "parent_id", required = false) String parentId,

            @Parameter(description = "Bộ lọc loại node (FILE hoặc FOLDER)")
            @RequestParam(value = "node_type", required = false) String nodeType,

            @Parameter(description = "Lọc theo tên")
            @RequestParam(value = "name", required = false) String name,

            @Parameter(description = "Lọc theo MIME type")
            @RequestParam(value = "mime_type", required = false) String mimeType,

            @Parameter(description = "Trang hiện tại (bắt đầu từ 0)")
            @RequestParam(value = "page", defaultValue = "0") int page,

            @Parameter(description = "Số lượng bản ghi mỗi trang")
            @RequestParam(value = "size", defaultValue = "20") int size,

            @Parameter(description = "Tiêu chí sắp xếp, ví dụ: deleted_at,desc hoặc name,asc")
            @RequestParam(value = "sort", defaultValue = "deleted_at,desc") List<String> sorts
    ) {
        UUID userId = (user != null) ? user.getId() : null;

        log.info("Lấy danh sách thùng rác: userId={}, parentId={}, nodeType={}, name={}, mimeType={}, page={}, size={}, sort={}",
                userId, parentId, nodeType, name, mimeType, page, size, sorts);

        PagedResponse<NodeResponse> response = fsNodeTrashService.getTrashNodes(
                userId, parentId, nodeType, name, mimeType, page, size, sorts
        );

        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách thành công", response));
    }

    @PostMapping("/restore")
    @Operation(summary = "Khôi phục danh sách node từ thùng rác")
    public ResponseEntity<ApiResponse<BatchOperationResponse<RestoreResult, NodeFailureResult>>> restoreNodes(
            @AuthenticationPrincipal FsUser user,
            @Valid @RequestBody BatchNodesRequest request
    ) {
        UUID userId = (user != null) ? user.getId() : null;

        List<String> nodeIds = (request != null && request.getNodeIds() != null) ? request.getNodeIds() : Collections.emptyList();
        log.info("Khôi phục nodes: userId={}, nodeIds={}", userId, nodeIds);

        BatchOperationResponse<RestoreResult, NodeFailureResult> response = fsNodeTrashService.restoreNodes(userId, nodeIds);

        return ResponseEntity.ok(ApiResponse.success("Thap tác thành công", response));
    }

    @DeleteMapping("/permanent")
    @Operation(summary = "Xóa vĩnh viễn node trong thùng rác hoặc toàn bộ thùng rác")
    public ResponseEntity<?> permanentDeleteTrash(
            @AuthenticationPrincipal FsUser user,
            @RequestBody(required = false) BatchNodesRequest request
    ) {
        UUID userId = (user != null) ? user.getId() : null;
        List<String> nodeIds = (request != null && request.getNodeIds() != null) ? request.getNodeIds() : Collections.emptyList();

        log.info("Xóa vĩnh viễn trash: userId={}, count={}", userId, nodeIds.size());

        BatchOperationResponse<Void, NodeFailureResult> batchResponse = fsNodeTrashService.permanentDeleteTrashAsync(userId, nodeIds);

        return ResponseEntity.ok()
                .body(ApiResponse.success("Thao tác thành công", batchResponse));
    }
}
