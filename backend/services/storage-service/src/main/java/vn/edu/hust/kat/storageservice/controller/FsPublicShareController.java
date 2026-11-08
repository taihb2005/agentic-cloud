package vn.edu.hust.kat.storageservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.hust.kat.common.api.response.ApiResponse;
import vn.edu.hust.kat.storageservice.dto.request.CreateFolderRequest;
import vn.edu.hust.kat.storageservice.dto.request.PublicAccessRequest;
import vn.edu.hust.kat.storageservice.dto.request.PublicDownloadRequest;
import vn.edu.hust.kat.storageservice.dto.request.UploadUrlRequest;
import vn.edu.hust.kat.storageservice.dto.response.*;
import vn.edu.hust.kat.storageservice.service.FsPublicShareService;

import java.util.List;

@RestController
@RequestMapping("/storage-service/shares/public")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Public Link Access API", description = "Các API truy cập, duyệt, tải và chỉnh sửa qua liên kết công khai")
public class FsPublicShareController {

    private final FsPublicShareService fsPublicShareService;

    @GetMapping("/{shareToken}")
    public ResponseEntity<ApiResponse<PublicShareInfoResponse>> getPublicShareInfo(
            @Parameter(description = "Token định danh của liên kết chia sẻ")
            @PathVariable("shareToken") String shareToken
    ) {
        log.info("Xem thông tin link công khai: shareToken={}", shareToken);
        PublicShareInfoResponse response = fsPublicShareService.getPublicShareInfo(shareToken);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{shareToken}/access")
    public ResponseEntity<ApiResponse<PublicAccessSessionResponse>> accessPublicShare(
            @Parameter(description = "Token định danh của liên kết chia sẻ")
            @PathVariable("shareToken") String shareToken,
            @RequestBody(required = false) PublicAccessRequest request
    ) {
        log.info("Yêu cầu mở khóa truy cập link công khai: shareToken={}", shareToken);
        PublicAccessSessionResponse response = fsPublicShareService.accessPublicShare(shareToken, request);
        return ResponseEntity.ok(ApiResponse.success("Truy cập link công khai thành công", response));
    }

    @GetMapping("/{shareToken}/nodes")
    public ResponseEntity<ApiResponse<PagedResponse<NodeResponse>>> getChildNodes(
            @Parameter(description = "Token định danh của liên kết chia sẻ")
            @PathVariable("shareToken") String shareToken,
            @Parameter(description = "Token phiên làm việc (bắt buộc nếu link có mật khẩu)")
            @RequestHeader(value = "X-Share-Access-Token", required = false) String shareAccessToken,

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
        log.info("Duyệt thư mục con qua link công khai: shareToken={}, parentId={}, page={}, size={}",
                shareToken, parentId, page, size);
        PagedResponse<NodeResponse> response = fsPublicShareService.getChildNodes(
                shareToken,
                shareAccessToken,
                parentId,
                nodeType,
                name,
                mimeType,
                page,
                size,
                sorts);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{shareToken}/download")
    @Operation(summary = "Tải tệp tin công khai qua Presigned GET URL")
    public ResponseEntity<ApiResponse<PresignedDownloadResponse>> downloadFile(
            @Parameter(description = "Token định danh của liên kết chia sẻ")
            @PathVariable("shareToken") String shareToken,

            @Parameter(description = "Token phiên làm việc (bắt buộc nếu link có mật khẩu)")
            @RequestHeader(value = "X-Share-Access-Token", required = false) String shareAccessToken,

            @RequestBody(required = false) PublicDownloadRequest request
    ) {
        log.info("Yêu cầu cấp presigned URL download qua link công khai: shareToken={}, nodeId={}",
                shareToken, request != null ? request.getNodeId() : null);
        PresignedDownloadResponse response = fsPublicShareService.downloadFile(shareToken, shareAccessToken, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{shareToken}/nodes/folders")
    @Operation(summary = "Tạo folder con trong shared folder (chỉ khi link có quyền EDIT)")
    public ResponseEntity<ApiResponse<NodeResponse>> createFolder(
            @Parameter(description = "Token định danh của liên kết chia sẻ")
            @PathVariable("shareToken") String shareToken,

            @Parameter(description = "Token phiên làm việc (bắt buộc nếu link có mật khẩu)")
            @RequestHeader(value = "X-Share-Access-Token", required = false) String shareAccessToken,

            @Valid @RequestBody CreateFolderRequest request
    ) {
        log.info("Tạo folder con trong shared folder: shareToken={}, name={}, parentId={}",
                shareToken, request.getName(), request.getParentId());
        NodeResponse response = fsPublicShareService.createFolder(shareToken, shareAccessToken, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo thư mục thành công", response, HttpStatus.CREATED));
    }

    @PostMapping("/{shareToken}/files/upload-urls")
    @Operation(summary = "Xin URL upload tệp mới vào shared folder (chỉ khi link có quyền EDIT)")
    public ResponseEntity<ApiResponse<UploadUrlsResponse>> getUploadUrls(
            @Parameter(description = "Token định danh của liên kết chia sẻ")
            @PathVariable("shareToken") String shareToken,

            @Parameter(description = "Token phiên làm việc (bắt buộc nếu link có mật khẩu)")
            @RequestHeader(value = "X-Share-Access-Token", required = false) String shareAccessToken,

            @Valid @RequestBody UploadUrlRequest request
    ) {
        log.info("Xin upload URLs trong shared folder: shareToken={}, số file={}",
                shareToken, request.getFiles() != null ? request.getFiles().size() : 0);
        UploadUrlsResponse response = fsPublicShareService.getUploadUrls(shareToken, shareAccessToken, request);
        return ResponseEntity.ok(ApiResponse.success("Thành công", response));
    }
}
