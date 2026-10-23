package vn.edu.hust.kat.storageservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hust.kat.storageservice.entity.FsNode;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.repository.FsNodeRepository;
import vn.edu.hust.kat.storageservice.utils.Constants;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FsNodeService {

    private final FsNodeRepository fsNodeRepository;

    @Transactional(readOnly = true)
    public Optional<FsNode> findRootNodeByUserId(UUID userId) {
        return fsNodeRepository.findByUserIdAndParentIsNull(userId);
    }

    /**
     * Khởi tạo Root Node.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public FsNode createRootNode(FsUser fsUser, String bucketName, String s3ObjectKey, String etag) {
        LocalDateTime now = LocalDateTime.now();

        FsNode rootNode = FsNode.builder()
                .user(fsUser)
                .parent(null)
                .name(Constants.STORAGE_ROOT_NAME)
                .isFolder(true)
                .materializedPath(Constants.STORAGE_ROOT_NAME)
                .nodeStatus("ACTIVE")
                .storageBucketName(bucketName)
                .s3ObjectKey(s3ObjectKey)
                .etag(etag)
                .sizeBytes(0L)
                .isStarred(false)
                .createdAt(now)
                .updatedAt(now)
                .build();

        FsNode savedNode = fsNodeRepository.save(rootNode);
        log.info("Đã tạo thư mục gốc (Root Node ID: {}, s3Key: {}) cho userId: {}",
                savedNode.getId(), s3ObjectKey, fsUser.getId());

        return savedNode;
    }

    /**
     * Hàm mở rộng để tạo các thư mục con hoặc file node sau này.
     */
    @Transactional
    public FsNode createChildNode(FsUser fsUser, FsNode parentNode, String name, boolean isFolder,
                                  String s3ObjectKey, Long sizeBytes) {
        LocalDateTime now = LocalDateTime.now();

        String materializedPath = parentNode.getMaterializedPath() + "." + name;

        FsNode childNode = FsNode.builder()
                .user(fsUser)
                .parent(parentNode)
                .name(name)
                .isFolder(isFolder)
                .materializedPath(materializedPath)
                .nodeStatus("ACTIVE")
                .storageBucketName(fsUser.getStorageBucketName())
                .s3ObjectKey(s3ObjectKey)
                .sizeBytes(sizeBytes != null ? sizeBytes : 0L)
                .isStarred(false)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return fsNodeRepository.save(childNode);
    }
}