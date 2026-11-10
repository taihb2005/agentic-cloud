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
import vn.edu.hust.kat.storageservice.dto.request.DownloadUrlRequest;
import vn.edu.hust.kat.storageservice.dto.response.DownloadUrlsResponse;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.service.FileDownloadService;

import java.util.UUID;

@RestController
@RequestMapping({"/storage-service/files", "/files"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "File Download API", description = "Các API yêu cầu cấp presigned URL và quản lý download file")
public class FileDownloadController {

    private final FileDownloadService fileDownloadService;

    @PostMapping("/download-urls")
    @Operation(summary = "Yêu cầu cấp Presigned URL để tải file từ MinIO")
    public ResponseEntity<ApiResponse<DownloadUrlsResponse>> getDownloadUrls(
            @AuthenticationPrincipal FsUser user,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Content-Disposition", required = false) String dispositionHeader,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Download", required = false) String downloadHeader,
            @org.springframework.web.bind.annotation.RequestParam(value = "disposition", required = false) String dispositionParam,
            @Valid @RequestBody DownloadUrlRequest request
    ) {
        UUID userId = (user != null) ? user.getId() : null;
        if (userId == null) {
            throw new BadRequestException("User ID không hợp lệ");
        }

        // Mặc định là attachment (tải về máy), trừ khi được yêu cầu rõ là inline preview
        boolean isAttachment = true;
        if ("inline".equalsIgnoreCase(dispositionParam) ||
                (dispositionHeader != null && dispositionHeader.toLowerCase().contains("inline"))) {
            isAttachment = false;
        }

        log.info("Nhận yêu cầu cấp presigned download URLs cho userId={}, số file={}, isAttachment={}",
                userId, request.getFiles() != null ? request.getFiles().size() : 0, isAttachment);

        DownloadUrlsResponse response = fileDownloadService.getDownloadUrls(userId, request, isAttachment);

        return ResponseEntity.ok()
                .body(ApiResponse.success("Thành công", response));
    }
}
