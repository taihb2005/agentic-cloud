package vn.edu.hust.kat.storageservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.hust.kat.common.api.response.ApiResponse;
import vn.edu.hust.kat.storageservice.dto.request.SetNodePasswordRequest;
import vn.edu.hust.kat.storageservice.dto.response.NodePasswordStatusResponse;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.service.FsNodePasswordService;

import java.util.UUID;

@RestController
@RequestMapping("/storage-service/nodes")
@RequiredArgsConstructor
@Slf4j
public class FsNodePasswordController {
    private final FsNodePasswordService fsNodePasswordService;

    @PutMapping("{id}/password")
    public ResponseEntity<ApiResponse<NodePasswordStatusResponse>> setOrChangePassword(
            @AuthenticationPrincipal FsUser user,
            @PathVariable Long id,
            @Valid @RequestBody SetNodePasswordRequest request
    ) {
        UUID userId = (user != null) ? user.getId() : null;

        log.info("Yêu cầu dặt mật khẩu cho node={}, request={}", id, request);

        NodePasswordStatusResponse response = fsNodePasswordService.setNodeSharePassword(userId, id, request);

        return ResponseEntity.ok()
                .body(ApiResponse.success(response));
    }

    @DeleteMapping("{id}/password")
    public ResponseEntity<ApiResponse<NodePasswordStatusResponse>> removePassword(
            @AuthenticationPrincipal FsUser user,
            @PathVariable Long id
    ) {
        UUID userId = (user != null) ? user.getId() : null;

        log.info("Yêu cầu xóa mật khẩu cho node={}", id);

        NodePasswordStatusResponse response = fsNodePasswordService.removePassword(userId, id);

        return ResponseEntity.ok()
                .body(ApiResponse.success(response));
    }
}
