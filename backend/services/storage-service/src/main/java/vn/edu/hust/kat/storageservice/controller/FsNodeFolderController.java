package vn.edu.hust.kat.storageservice.controller;

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
import vn.edu.hust.kat.storageservice.dto.response.NodeResponse;
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

}
