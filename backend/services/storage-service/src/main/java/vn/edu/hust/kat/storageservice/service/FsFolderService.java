package vn.edu.hust.kat.storageservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.common.api.exception.ResourceNotFoundException;
import vn.edu.hust.kat.storageservice.dto.response.MinioObjectResponse;
import vn.edu.hust.kat.storageservice.dto.response.NodeResponse;
import vn.edu.hust.kat.storageservice.entity.FsNode;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.repository.FsNodeRepository;
import vn.edu.hust.kat.storageservice.repository.FsUserRepository;
import vn.edu.hust.kat.storageservice.utils.NodePathUtilities;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FsFolderService {
    private final FsUserRepository fsUserRepository;
    private final FsNodeRepository fsNodeRepository;
    private final FsNodeService fsNodeService;
    private final MinioStorageService minioStorageService;

    @Transactional
    public NodeResponse createFolder(UUID userId, String parentId, String name) {
        if (userId == null) {
            throw new BadCredentialsException("User Id không hợp lệ");
        }

        if (name == null || name.isBlank()) {
            throw new BadRequestException("Tên thư mục không được để trống");
        }
        String trimmedName = name.trim();

        FsUser user = fsUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User này không có không gian lưu trữ trên hệ thống"));

        String bucketName = user.getStorageBucketName();
        String storagePrefix = user.getStoragePrefix();

        // 1. Kiểm tra tính hợp lệ của thư mục cha
        FsNode parent = fsNodeService.resolveAndValidateNode(userId, parentId, true);

        // 2. Kiểm tra trùng tên trong thư mục cha (ở tầng logic Database)
        if (fsNodeRepository.existsByParentAndName(parent, trimmedName)) {
            throw new BadRequestException("Thư mục có tên '" + trimmedName + "' đã tồn tại.");
        }

        // 3. Khởi tạo node và lưu trước để lấy ID phục vụ tính ltree
        FsNode newFolder = FsNode.builder()
                .name(trimmedName)
                .parent(parent)
                .user(user)
                .nodeStatus(FsNode.Status.ACTIVE)
                .isFolder(true)
                .sizeBytes(0L)
                .build();

        newFolder = fsNodeRepository.save(newFolder);

        // 4. Tính toán materialized path (ltree) - CHỈ DÙNG CHO DATABASE
        String newNodeId = String.valueOf(newFolder.getId());
        String parentNodePath = parent.getMaterializedPath();
        String newNodePath = NodePathUtilities.join(parentNodePath, newNodeId);
        newFolder.setMaterializedPath(newNodePath);

        String createdObjectKey = null;
        try {
            // 5. Yêu cầu MinIO tạo thư mục vật lý .
            // MinIO Storage Service tự sinh Key phẳng (VD: "user_prefix/uuid/") và trả về trong response.
            MinioObjectResponse objectResponse = minioStorageService.createDirectory(bucketName, storagePrefix);
            createdObjectKey = objectResponse.getObjectKey();

            // 6. Gán metadata thực tế do MinIO trả về vào node
            newFolder.setEtag(objectResponse.getEtag());
            newFolder.setS3ObjectKey(createdObjectKey);
            newFolder.setStorageBucketName(objectResponse.getBucketName());

            newFolder = fsNodeRepository.save(newFolder);
            return NodeResponse.from(newFolder);

        } catch (Exception e) {
            log.error("Lỗi khi tạo thư mục trên MinIO hoặc lưu Database: {}", e.getMessage(), e);
            // Dọn dẹp object rác trên MinIO nếu lưu DB thất bại
            if (createdObjectKey != null) {
                minioStorageService.deleteObjectSilently(bucketName, createdObjectKey);
            }
            throw new RuntimeException("Khởi tạo thư mục thất bại: " + e.getMessage(), e);
        }
    }
}
