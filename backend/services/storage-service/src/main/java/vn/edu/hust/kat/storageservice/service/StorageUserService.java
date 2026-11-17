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
import vn.edu.hust.kat.storageservice.repository.FsNodeRepository;
import vn.edu.hust.kat.storageservice.repository.FsNodeShareUserRepository;
import vn.edu.hust.kat.storageservice.repository.FsShareRepository;
import vn.edu.hust.kat.storageservice.repository.FsUserRepository;
import vn.edu.hust.kat.storageservice.utils.Constants;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageUserService {

    private final FsUserRepository fsUserRepository;
    private final FsNodeRepository fsNodeRepository;
    private final FsShareRepository fsShareRepository;
    private final FsNodeShareUserRepository fsNodeShareUserRepository;
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

    /**
     * Xóa cứng (hard delete) toàn bộ không gian lưu trữ, dữ liệu node, chia sẻ và các object trên MinIO của người dùng.
     * Nếu một file vật lý trên MinIO đang được người dùng khác tham chiếu (do cơ chế deduplication theo file_hash),
     * hệ thống sẽ giữ lại object vật lý đó trên MinIO và chỉ xóa các object không còn ai dùng.
     */
    @Transactional
    public void deleteUserStorage(UUID userId) {
        if (userId == null) {
            throw new BadRequestException("Id không hợp lệ");
        }
        log.info("Bắt đầu tiến trình xóa cứng (hard delete) dữ liệu lưu trữ của userId: {}", userId);

        Optional<FsUser> fsUserOpt = fsUserRepository.findById(userId);
        List<FsNode> userNodes = fsNodeRepository.findAllByUserId(userId);

        String defaultBucket = fsUserOpt.map(FsUser::getStorageBucketName)
                .filter(b -> !b.isBlank())
                .orElseGet(minioProperties::getDefaultBucket);
        String storagePrefix = fsUserOpt.map(FsUser::getStoragePrefix)
                .filter(p -> !p.isBlank())
                .orElseGet(() -> {
                    String pattern = minioProperties.getUserPrefixPattern();
                    return (pattern != null && !pattern.isBlank())
                            ? String.format(pattern, userId)
                            : "users/" + userId + "/";
                });

        // 1. Gom toàn bộ (bucketName -> Set<objectKey>) thuộc về user từ DB và từ prefix trên MinIO
        Map<String, Set<String>> bucketToCandidateKeys = new LinkedHashMap<>();

        for (FsNode node : userNodes) {
            String key = node.getS3ObjectKey();
            if (key != null && !key.isBlank()) {
                String bucket = (node.getStorageBucketName() != null && !node.getStorageBucketName().isBlank())
                        ? node.getStorageBucketName()
                        : defaultBucket;
                bucketToCandidateKeys.computeIfAbsent(bucket, k -> new LinkedHashSet<>()).add(key);
            }
        }

        List<String> prefixKeys = minioStorageService.listObjectKeysByPrefix(defaultBucket, storagePrefix);
        if (!prefixKeys.isEmpty()) {
            bucketToCandidateKeys.computeIfAbsent(defaultBucket, k -> new LinkedHashSet<>()).addAll(prefixKeys);
        }

        // 2. Kiểm tra các objectKey đang được user khác tham chiếu (do cơ chế deduplication)
        Set<String> allCandidateKeys = new LinkedHashSet<>();
        bucketToCandidateKeys.values().forEach(allCandidateKeys::addAll);

        Set<String> keysReferencedByOthers = allCandidateKeys.isEmpty()
                ? Collections.emptySet()
                : fsNodeRepository.findObjectKeysReferencedByOtherUsers(userId, allCandidateKeys);

        // 3. Xóa cứng toàn bộ dữ liệu trong DB theo thứ tự phụ thuộc khóa ngoại
        int deletedShareUsers = fsNodeShareUserRepository.deleteAllByUserIdOrNodeOwnerId(userId);
        int deletedShares = fsShareRepository.deleteAllByUserIdOrNodeOwnerId(userId);
        int deletedNodes = fsNodeRepository.deleteAllByUserId(userId);
        int deletedUsers = fsUserRepository.deleteHardById(userId);

        log.info("Đã xóa cứng dữ liệu DB cho userId={}: fs_users={}, fs_nodes={}, fs_shares={}, fs_node_share_user={}",
                userId, deletedUsers, deletedNodes, deletedShares, deletedShareUsers);

        // 4. Xóa các object vật lý trên MinIO (giữ lại các file đang được user khác tham chiếu)
        int deletedMinioObjects = 0;
        int retainedDeduplicatedObjects = 0;
        for (Map.Entry<String, Set<String>> entry : bucketToCandidateKeys.entrySet()) {
            String bucket = entry.getKey();
            for (String objectKey : entry.getValue()) {
                if (keysReferencedByOthers.contains(objectKey)) {
                    retainedDeduplicatedObjects++;
                    log.info("Giữ lại object trên MinIO do đang được user khác tham chiếu (deduplication): bucket={}, key={}",
                            bucket, objectKey);
                } else {
                    minioStorageService.deleteObjectSilently(bucket, objectKey);
                    deletedMinioObjects++;
                }
            }
        }

        log.info("Hoàn tất xóa cứng storage trên MinIO cho userId={}: đã xóa {} objects, giữ lại {} objects (deduplicated)",
                userId, deletedMinioObjects, retainedDeduplicatedObjects);
    }
}