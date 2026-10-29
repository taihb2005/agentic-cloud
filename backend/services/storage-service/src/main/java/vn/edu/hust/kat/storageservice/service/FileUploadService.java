package vn.edu.hust.kat.storageservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.common.api.exception.ResourceNotFoundException;
import vn.edu.hust.kat.storageservice.config.MinioProperties;
import vn.edu.hust.kat.storageservice.dto.request.FileUploadItemRequest;
import vn.edu.hust.kat.storageservice.dto.request.UploadUrlRequest;
import vn.edu.hust.kat.storageservice.dto.response.PresignedUrlResponse;
import vn.edu.hust.kat.storageservice.dto.response.UploadUrlsResponse;
import vn.edu.hust.kat.storageservice.entity.FsNode;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.repository.FsNodeRepository;
import vn.edu.hust.kat.storageservice.repository.FsUserRepository;
import vn.edu.hust.kat.storageservice.utils.NodePathUtilities;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadService {

    private final FsUserRepository fsUserRepository;
    private final FsNodeRepository fsNodeRepository;
    private final FsNodeService fsNodeService;
    private final QuotaService quotaService;
    private final MinioStorageService minioStorageService;
    private final MinioProperties minioProperties;

    @Transactional
    public UploadUrlsResponse getUploadUrls(UUID userId, UploadUrlRequest request) {
        if (userId == null) {
            throw new BadRequestException("User ID không hợp lệ");
        }

        if (request == null || request.getFiles() == null || request.getFiles().isEmpty()) {
            throw new BadRequestException("Danh sách tệp tin yêu cầu tải lên không được để trống");
        }

        FsUser user = fsUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng chưa được khởi tạo không gian lưu trữ"));

        // 1. Kiểm tra Quota của User
        long totalRequestedBytes = request.getFiles().stream()
                .mapToLong(f -> f.getSize() != null ? f.getSize() : 0L)
                .sum();

        long currentQuota = user.getStorageCurrentQuotaBytes() != null ? user.getStorageCurrentQuotaBytes() : 0L;
        long maxQuota = (user.getStorageMaxQuotaBytes() != null && user.getStorageMaxQuotaBytes() > 0)
                ? user.getStorageMaxQuotaBytes()
                : minioProperties.getDefaultQuotaBytes();

        if (currentQuota + totalRequestedBytes > maxQuota) {
            log.warn("Dung lượng vượt quá quota cho userId={}: current={}, requested={}, max={}",
                    userId, currentQuota, totalRequestedBytes, maxQuota);
            throw new BadRequestException("Dung lượng tải lên vượt quá giới hạn lưu trữ cho phép (Quota exceeded)");
        }

        // Kiểm tra trùng lặp tên trong chính request
        Set<String> seenNamesInFolder = new HashSet<>();
        for (FileUploadItemRequest file : request.getFiles()) {
            String folderKey = (file.getParentId() == null ? "root" : file.getParentId().trim()) + ":" + file.getName().trim();
            if (!seenNamesInFolder.add(folderKey)) {
                throw new BadRequestException("Tên file bị trùng lặp trong cùng thư mục: " + file.getName());
            }
        }

        List<PresignedUrlResponse> presignedUrls = new ArrayList<>();

        for (FileUploadItemRequest item : request.getFiles()) {
            String fileName = item.getName().trim();
            String fileHash = item.getHash().trim();
            Long fileSize = item.getSize();
            String mimeType = item.getMimeType() != null && !item.getMimeType().isBlank() ? item.getMimeType().trim() : null;

            // Kiểm tra thư mục cha
            FsNode parentFolder = fsNodeService.resolveAndValidateFolder(userId, item.getParentId(), true);

            // Kiểm tra trùng tên file trong thư mục đích
            if (fsNodeRepository.existsActiveNodeByParentAndName(userId, parentFolder.getId(), fileName, -1L)) {
                throw new BadRequestException("Tệp tin có tên '" + fileName + "' đã tồn tại trong thư mục đích.");
            }

            // 2. Kiểm tra fileHash để xem file đã tồn tại trên hệ thống MinIO chưa (Deduplication)
            Optional<FsNode> existingActiveFile = fsNodeRepository.findFirstByFileHashAndNodeStatusAndDeletedAtIsNull(
                    fileHash, FsNode.Status.ACTIVE
            );

            boolean isDeduplicated = false;
            FsNode matchedFile = null;

            if (existingActiveFile.isPresent()) {
                matchedFile = existingActiveFile.get();
                // Xác nhận lại file vật lý có tồn tại trên MinIO
                if (minioStorageService.doesObjectExist(matchedFile.getStorageBucketName(), matchedFile.getS3ObjectKey())) {
                    isDeduplicated = true;
                }
            }

            if (isDeduplicated) {
                // File đã có trên MinIO -> Deduplication thành công, instant_uploaded = true
                FsNode instantNode = FsNode.builder()
                        .name(fileName)
                        .parent(parentFolder)
                        .user(user)
                        .nodeStatus(FsNode.Status.ACTIVE)
                        .isFolder(false)
                        .sizeBytes(fileSize)
                        .mimeType(mimeType != null ? mimeType : matchedFile.getMimeType())
                        .fileHash(fileHash)
                        .storageBucketName(matchedFile.getStorageBucketName())
                        .s3ObjectKey(matchedFile.getS3ObjectKey())
                        .etag(matchedFile.getEtag())
                        .isStarred(false)
                        .build();

                instantNode = fsNodeRepository.save(instantNode);

                String newNodePath = NodePathUtilities.join(parentFolder.getMaterializedPath(), String.valueOf(instantNode.getId()));
                instantNode.setMaterializedPath(newNodePath);
                instantNode = fsNodeRepository.save(instantNode);

                // Cập nhật quota cho user ngay lập tức vì file đã ACTIVE
                quotaService.addToUserCurrentQuota(userId, fileSize);

                presignedUrls.add(PresignedUrlResponse.builder()
                        .nodeId(String.valueOf(instantNode.getId()))
                        .url(null)
                        .instantUploaded(true)
                        .expiresAt(null)
                        .build());

                log.info("File '{}' (hash: {}) đã tồn tại trên MinIO. Đã tạo node {} với instant_uploaded=true cho user {}",
                        fileName, fileHash, instantNode.getId(), userId);

            } else {
                // 3. Tạo một bản ghi File Node trong Database với trạng thái PENDING
                String bucketName = user.getStorageBucketName() != null ? user.getStorageBucketName() : minioProperties.getDefaultBucket();
                String storagePrefix = (user.getStoragePrefix() != null && !user.getStoragePrefix().isBlank())
                        ? user.getStoragePrefix()
                        : String.format(minioProperties.getUserPrefixPattern(), userId);
                String normalizedPrefix = storagePrefix.endsWith("/") ? storagePrefix : storagePrefix + "/";
                String s3ObjectKey = normalizedPrefix + UUID.randomUUID();

                FsNode pendingNode = FsNode.builder()
                        .name(fileName)
                        .parent(parentFolder)
                        .user(user)
                        .nodeStatus(FsNode.Status.PENDING)
                        .isFolder(false)
                        .sizeBytes(fileSize)
                        .mimeType(mimeType)
                        .fileHash(fileHash)
                        .storageBucketName(bucketName)
                        .s3ObjectKey(s3ObjectKey)
                        .isStarred(false)
                        .build();

                pendingNode = fsNodeRepository.save(pendingNode);

                String newNodePath = NodePathUtilities.join(parentFolder.getMaterializedPath(), String.valueOf(pendingNode.getId()));
                pendingNode.setMaterializedPath(newNodePath);
                pendingNode = fsNodeRepository.save(pendingNode);

                // 4. Trả về Presigned URL với quyền PUT
                int expirySeconds = minioProperties.getPresignedUrlExpirySeconds();
                String presignedUrl = minioStorageService.generatePresignedUploadUrl(bucketName, s3ObjectKey, expirySeconds);
                Instant expiresAt = Instant.now().plusSeconds(expirySeconds);

                presignedUrls.add(PresignedUrlResponse.builder()
                        .nodeId(String.valueOf(pendingNode.getId()))
                        .url(presignedUrl)
                        .instantUploaded(false)
                        .expiresAt(expiresAt)
                        .build());

                log.info("Đã tạo pending node {} và presigned PUT URL cho file '{}' (hash: {})",
                        pendingNode.getId(), fileName, fileHash);
            }
        }

        return UploadUrlsResponse.builder()
                .presignedUrls(presignedUrls)
                .build();
    }
}
