package vn.edu.hust.kat.storageservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.common.api.exception.ResourceNotFoundException;
import vn.edu.hust.kat.storageservice.config.MinioProperties;
import vn.edu.hust.kat.storageservice.dto.request.InitUserStorageRequest;
import vn.edu.hust.kat.storageservice.dto.response.MinioObjectResponse;
import vn.edu.hust.kat.storageservice.dto.response.UserStorageResponse;
import vn.edu.hust.kat.storageservice.entity.FsNode;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.repository.FsUserRepository;
import vn.edu.hust.kat.storageservice.utils.Constants;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageUserService {

    private final FsUserRepository fsUserRepository;
    private final FsNodeService fsNodeService;
    private final MinioStorageService minioStorageService;
    private final MinioProperties minioProperties;

    /**
     * Khởi tạo không gian lưu trữ cho người dùng.
     * Đảm bảo: Tạo folder trên MinIO thành công -> Nhận s3ObjectKey -> Tạo Metadata trong DB.
     */
    @Transactional
    public UserStorageResponse initUserStorage(InitUserStorageRequest request) {
        UUID userId = request.getUserId();
        log.info("Bắt đầu tiến trình khởi tạo không gian lưu trữ cho userId: {}", userId);

        // 1. Kiểm tra Idempotency: Người dùng đã tồn tại chưa
        Optional<FsUser> existingUserOpt = fsUserRepository.findById(userId);
        if (existingUserOpt.isPresent()) {
            return getUserStorage(existingUserOpt.get().getId());
        }

        // 2. Xác định các thông số cấu hình mặc định
        String bucketName = minioProperties.getDefaultBucket();
        String storagePrefix = String.format(minioProperties.getUserPrefixPattern(), userId);
        Long quotaBytes = request.getCustomQuotaBytes() != null
                ? request.getCustomQuotaBytes()
                : minioProperties.getDefaultQuotaBytes();

        log.debug("Cấu hình lưu trữ: bucket={}, prefix={}, quota={} bytes", bucketName, storagePrefix, quotaBytes);

        // 3. Gọi MinIO tạo folder TRƯỚC. Chỉ khi MinIO trả về s3ObjectKey hợp lệ mới đi tiếp.
        MinioObjectResponse createdObject = minioStorageService.createDirectory(bucketName, storagePrefix);

        // 4. Lưu Metadata (FsUser + Root FsNode) sau khi MinIO đã thành công
        try {
            LocalDateTime now = LocalDateTime.now();

            FsUser fsUser = FsUser.builder()
                    .id(userId)
                    .storagePrefix(storagePrefix)
                    .storageBucketName(createdObject.getBucketName())
                    .storageMaxQuotaBytes(quotaBytes)
                    .storageCurrentQuotaBytes(0L)
                    .build();

            fsUser = fsUserRepository.save(fsUser);
            log.info("Đã lưu cấu hình FsUser cho userId: {}", userId);

            // Khởi tạo thư mục trong database nếu như tạo thư mục thành công
            FsNode rootNode = fsNodeService.createRootNode(fsUser, createdObject.getBucketName(), createdObject.getObjectKey(), createdObject.getEtag());

            return UserStorageResponse.builder()
                    .userId(fsUser.getId())
                    .storagePrefix(fsUser.getStoragePrefix())
                    .storageBucketName(fsUser.getStorageBucketName())
                    .storageMaxQuotaBytes(fsUser.getStorageMaxQuotaBytes())
                    .storageCurrentQuotaBytes(fsUser.getStorageCurrentQuotaBytes())
                    .rootNodeId(rootNode.getId())
                    .rootNodeName(rootNode.getName())
                    .rootNodePath(rootNode.getMaterializedPath())
                    .createdAt(fsUser.getCreatedAt())
                    .updatedAt(fsUser.getUpdatedAt())
                    .build();

        } catch (Exception dbEx) {
            // Nếu lưu DB thất bại, xóa ngay folder vừa tạo để tránh tồn tại data rác
            log.error("Lỗi khi lưu metadata vào DB cho userId: {}. Tiến hành rollback trên MinIO...", userId, dbEx);
            minioStorageService.deleteObjectSilently(bucketName, createdObject.getObjectKey());
            throw dbEx;
        }
    }

    public boolean isUserNew(UUID userId) {
        return fsUserRepository.existsById(userId);
    }

    public UserStorageResponse getUserStorage(UUID userId) {
        if (userId == null || userId.toString().isEmpty() || userId.toString().isBlank()) {
            throw new BadRequestException("Id không hợp lệ");
        }
        FsUser fsUser = fsUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Thông tin người dùng không tồn tại"));
        log.info("Đang lấy thông tin người dùng có id {}", userId);

        Optional<FsNode> rootNodeOpt = fsNodeService.findRootNodeByUserId(userId);
        Long rootNodeId = rootNodeOpt.map(FsNode::getId).orElse(null);
        String rootNodePath = rootNodeOpt.map(FsNode::getMaterializedPath).orElse(Constants.STORAGE_ROOT_NAME);

        return UserStorageResponse.builder()
                .userId(fsUser.getId())
                .storagePrefix(fsUser.getStoragePrefix())
                .storageBucketName(fsUser.getStorageBucketName())
                .storageMaxQuotaBytes(fsUser.getStorageMaxQuotaBytes())
                .storageCurrentQuotaBytes(fsUser.getStorageCurrentQuotaBytes())
                .rootNodeId(rootNodeId)
                .rootNodeName(Constants.STORAGE_ROOT_NAME)
                .rootNodePath(rootNodePath)
                .createdAt(fsUser.getCreatedAt())
                .updatedAt(fsUser.getUpdatedAt())
                .build();
    }
}