package vn.edu.hust.kat.storageservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hust.kat.common.api.response.ApiResponse;
import vn.edu.hust.kat.storageservice.dto.request.CreateFolderRequest;
import vn.edu.hust.kat.storageservice.dto.request.CreateRecursiveFolderRequest;
import vn.edu.hust.kat.storageservice.dto.response.NodeResponse;
import vn.edu.hust.kat.storageservice.dto.response.RecursiveFolderCreationResponse;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.service.FsNodeFolderService;

import java.util.UUID;

@RestController
@RequestMapping({"/storage-service/nodes/folders"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Node API", description = "Các API quản lý thư mục")
public class FsNodeFolderController {

    private final FsNodeFolderService fsNodeFolderService;

    @PostMapping("")
    @Operation(summary = "Tạo mới một thư mục con đơn lẻ")
    public ResponseEntity<ApiResponse<NodeResponse>> createFolder(
            @AuthenticationPrincipal FsUser user,
            @RequestBody CreateFolderRequest request
    ) {
        UUID userId = (user != null) ? user.getId() : null;

        log.info("Tạo thư mục con cho userId={}, parentId={}, name={}", userId, request.getParentId(), request.getName());

        NodeResponse nodeResponse = fsNodeFolderService.createFolder(userId, request.getParentId(), request.getName());

        return ResponseEntity.ok()
                .body(ApiResponse.success("Tạo thư mục thành công", nodeResponse));
    }

    @PostMapping("/recursive")
    @Operation(summary = "Tạo cây thư mục đệ quy theo đường dẫn (ví dụ: A/B/C, tối đa 10 cấp)")
    public ResponseEntity<ApiResponse<RecursiveFolderCreationResponse>> createFolderRecursive(
            @AuthenticationPrincipal FsUser user,
            @RequestBody CreateRecursiveFolderRequest request
    ) {
        UUID userId = (user != null) ? user.getId() : null;

        log.info("Tạo thư mục đệ quy cho userId={}, parentId={}, path={}",
                userId, request != null ? request.getParentId() : null, request != null ? request.getPath() : null);

        RecursiveFolderCreationResponse response = fsNodeFolderService.createFolderRecursive(
                userId,
                request != null ? request.getParentId() : null,
                request != null ? request.getPath() : null
        );

        return ResponseEntity.ok()
                .body(ApiResponse.success("Tạo thư mục đệ quy thành công", response));
    }

}
