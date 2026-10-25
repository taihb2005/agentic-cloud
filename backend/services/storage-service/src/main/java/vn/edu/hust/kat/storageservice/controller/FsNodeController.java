package vn.edu.hust.kat.storageservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.SpringSecurityMessageSource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hust.kat.common.api.response.ApiResponse;
import vn.edu.hust.kat.storageservice.dto.response.NodeResponse;
import vn.edu.hust.kat.storageservice.dto.response.PagedResponse;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.service.FsNodeService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping({
        "/storage-service/nodes",
        "/nodes",
        "/api/v1/storage-service/nodes"
})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Node API", description = "Các API quản lý file và thư mục")
public class FsNodeController {

    private final FsNodeService fsNodeService;

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
}
