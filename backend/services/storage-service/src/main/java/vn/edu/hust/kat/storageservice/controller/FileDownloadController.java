package vn.edu.hust.kat.storageservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.common.api.response.ApiResponse;
import vn.edu.hust.kat.storageservice.dto.request.UploadUrlRequest;
import vn.edu.hust.kat.storageservice.dto.response.UploadUrlsResponse;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.service.FileUploadService;

import java.util.UUID;

@RestController
@RequestMapping({"/storage-service/files", "/files"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "File Upload API", description = "Các API yêu cầu cấp presigned URL và quản lý download file")
public class FileDownloadController {

    private final FileUploadService fileUploadService;

    @PostMapping("/download-urls")
    @Operation(summary = "Yêu cầu cấp Presigned URL để tải file từ MinIO")
    public ResponseEntity<ApiResponse<UploadUrlsResponse>> getUploadUrls(
            @AuthenticationPrincipal FsUser user,
            @Valid @RequestBody UploadUrlRequest request
    ) {
        UUID userId = (user != null) ? user.getId() : null;
        if (userId == null) {
            throw new BadRequestException("User ID không hợp lệ");
        }

        log.info("Nhận yêu cầu cấp presigned upload URLs cho userId={}, số file={}",
                userId, request.getFiles() != null ? request.getFiles().size() : 0);

        UploadUrlsResponse response = fileUploadService.getUploadUrls(userId, request);

        return ResponseEntity.ok()
                .body(ApiResponse.success("Thành công", response));
    }
}
