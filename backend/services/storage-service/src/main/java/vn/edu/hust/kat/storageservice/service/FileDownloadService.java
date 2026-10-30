package vn.edu.hust.kat.storageservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.common.api.exception.ResourceNotFoundException;
import vn.edu.hust.kat.storageservice.config.MinioProperties;
import vn.edu.hust.kat.storageservice.dto.request.DownloadUrlRequest;
import vn.edu.hust.kat.storageservice.dto.request.FileDownloadItemRequest;
import vn.edu.hust.kat.storageservice.dto.response.DownloadUrlItemResponse;
import vn.edu.hust.kat.storageservice.dto.response.DownloadUrlsResponse;
import vn.edu.hust.kat.storageservice.entity.FsNode;
import vn.edu.hust.kat.storageservice.repository.FsNodeRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileDownloadService {

    private final FsNodeRepository fsNodeRepository;
    private final MinioStorageService minioStorageService;
    private final MinioProperties minioProperties;

    /**
     * Tạo danh sách Presigned Download URL cho các node file được yêu cầu.
     *
     * @param userId  UUID của người dùng đang thực hiện yêu cầu
     * @param request Danh sách node_id cần tải xuống
     * @return DownloadUrlsResponse chứa các presigned GET URL
     */
    @Transactional(readOnly = true)
    public DownloadUrlsResponse getDownloadUrls(UUID userId, DownloadUrlRequest request) {
        if (userId == null) {
            throw new BadRequestException("User ID không hợp lệ");
        }

        if (request == null || request.getFiles() == null || request.getFiles().isEmpty()) {
            throw new BadRequestException("Danh sách tệp tin yêu cầu tải xuống không được để trống");
        }

        int expirySeconds = minioProperties.getPresignedUrlExpirySeconds();
        List<DownloadUrlItemResponse> downloadUrls = new ArrayList<>();

        for (FileDownloadItemRequest item : request.getFiles()) {
            String rawNodeId = item.getNodeId();

            long nodeId;
            try {
                nodeId = Long.parseLong(rawNodeId.trim());
            } catch (NumberFormatException e) {
                throw new BadRequestException("node_id không hợp lệ: " + rawNodeId);
            }

            // Tìm node trong DB và xác thực quyền sở hữu
            FsNode node = fsNodeRepository.findById(nodeId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy file với node_id=" + rawNodeId));

            // Chỉ cho phép tải file (không phải folder)
            if (Boolean.TRUE.equals(node.getIsFolder())) {
                throw new BadRequestException("node_id=" + rawNodeId + " là thư mục, không thể tải xuống.");
            }

            // Kiểm tra quyền sở hữu: chỉ chủ sở hữu mới được tạo download URL
            if (!userId.equals(node.getUser().getId())) {
                throw new BadRequestException("Bạn không có quyền truy cập file với node_id=" + rawNodeId);
            }

            // Chỉ cho phép tải các file đang ACTIVE
            if (node.getNodeStatus() != FsNode.Status.ACTIVE) {
                throw new BadRequestException(
                        "File với node_id=" + rawNodeId + " không ở trạng thái ACTIVE (trạng thái hiện tại: "
                                + node.getNodeStatus() + ")");
            }

            // Kiểm tra file vật lý tồn tại trên MinIO
            if (!minioStorageService.doesObjectExist(node.getStorageBucketName(), node.getS3ObjectKey())) {
                throw new ResourceNotFoundException(
                        "Không tìm thấy file vật lý trên hệ thống lưu trữ cho node_id=" + rawNodeId);
            }

            // Tạo Presigned GET URL
            String presignedUrl = minioStorageService.generatePresignedDownloadUrl(
                    node.getStorageBucketName(),
                    node.getS3ObjectKey(),
                    expirySeconds
            );

            Instant expiresAt = Instant.now().plusSeconds(expirySeconds);

            downloadUrls.add(DownloadUrlItemResponse.builder()
                    .nodeId(String.valueOf(node.getId()))
                    .fileName(node.getName())
                    .url(presignedUrl)
                    .expiresAt(expiresAt)
                    .mimeType(node.getMimeType())
                    .sizeBytes(node.getSizeBytes())
                    .build());

            log.info("Đã tạo presigned GET URL cho file nodeId={}, userId={}", nodeId, userId);
        }

        return DownloadUrlsResponse.builder()
                .downloadUrls(downloadUrls)
                .build();
    }
}
