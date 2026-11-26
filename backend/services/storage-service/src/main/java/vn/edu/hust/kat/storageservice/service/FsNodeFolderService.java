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
import vn.edu.hust.kat.storageservice.dto.response.RecursiveFolderCreationResponse;
import vn.edu.hust.kat.storageservice.entity.FsNode;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.repository.FsNodeRepository;
import vn.edu.hust.kat.storageservice.repository.FsUserRepository;
import vn.edu.hust.kat.storageservice.utils.NodePathUtilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FsNodeFolderService {
    public static final int MAX_RECURSIVE_DEPTH = 10;

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
        if (trimmedName.contains("/") || trimmedName.contains("\\")) {
            throw new BadRequestException("Tên thư mục đơn lẻ không được chứa dấu '/' hoặc '\\'. Hãy sử dụng API tạo thư mục đệ quy (/nodes/folders/recursive).");
        }

        FsUser user = fsUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User này không có không gian lưu trữ trên hệ thống"));

        String bucketName = user.getStorageBucketName();
        String storagePrefix = user.getStoragePrefix();

        // Kiểm tra tính hợp lệ của thư mục cha
        FsNode parent = fsNodeService.resolveAndValidateNode(userId, parentId, true);

        // Kiểm tra xem thư mục cha có phải là một thư mục không
        if (!Boolean.TRUE.equals(parent.getIsFolder())) {
            throw new BadRequestException("Thư mục phải được tạo trong thư mục khác");
        }

        // Kiểm tra trùng tên trong thư mục cha (chỉ kiểm tra node đang active)
        if (fsNodeRepository.findActiveNodeByParentIdAndName(userId, parent.getId(), trimmedName).isPresent()
                || fsNodeRepository.existsByParentAndName(parent, trimmedName)) {
            throw new BadRequestException("Thư mục có tên '" + trimmedName + "' đã tồn tại.");
        }

        List<String> createdObjectKeys = new ArrayList<>();
        try {
            FsNode newFolder = createSingleFolderInternal(user, parent, trimmedName, bucketName, storagePrefix, createdObjectKeys);
            return NodeResponse.from(newFolder);
        } catch (BadRequestException | ResourceNotFoundException e) {
            cleanupMinioObjects(bucketName, createdObjectKeys);
            throw e;
        } catch (Exception e) {
            log.error("Lỗi khi tạo thư mục trên MinIO hoặc lưu Database: {}", e.getMessage(), e);
            cleanupMinioObjects(bucketName, createdObjectKeys);
            throw new RuntimeException("Khởi tạo thư mục thất bại: " + e.getMessage(), e);
        }
    }

    /**
     * Tạo cây thư mục đệ quy theo đường dẫn (ví dụ: "A/B/C", tối đa 10 cấp).
     * - Các thư mục cha trung gian nếu đã tồn tại (và là FOLDER) sẽ được tái sử dụng.
     * - Nếu bất kỳ cấp trung gian nào là FILE, hoặc thư mục lá cuối cùng đã tồn tại -> ném BadRequestException.
     * - Nếu xảy ra lỗi ở bất kỳ bước nào, tự động dọn sạch toàn bộ object đã tạo trên MinIO và rollback DB.
     */
    @Transactional
    public RecursiveFolderCreationResponse createFolderRecursive(UUID userId, String parentId, String rawPath) {
        if (userId == null) {
            throw new BadCredentialsException("User Id không hợp lệ");
        }

        List<String> segments = parseAndValidateRecursivePath(rawPath);

        FsUser user = fsUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User này không có không gian lưu trữ trên hệ thống"));

        String bucketName = user.getStorageBucketName();
        String storagePrefix = user.getStoragePrefix();

        FsNode currentParent = fsNodeService.resolveAndValidateNode(userId, parentId, true);
        if (!Boolean.TRUE.equals(currentParent.getIsFolder())) {
            throw new BadRequestException("Thư mục phải được tạo trong thư mục khác");
        }

        List<String> createdObjectKeys = new ArrayList<>();
        List<NodeResponse> createdNodes = new ArrayList<>();
        Long firstCreatedNodeId = null;
        int reusedCount = 0;

        try {
            for (int i = 0; i < segments.size(); i++) {
                String segmentName = segments.get(i);
                boolean isLeaf = (i == segments.size() - 1);

                Optional<FsNode> existingOpt = fsNodeRepository.findActiveNodeByParentIdAndName(
                        userId,
                        currentParent.getId(),
                        segmentName
                );

                if (existingOpt.isPresent()) {
                    FsNode existing = existingOpt.get();
                    if (isLeaf) {
                        throw new BadRequestException("Thư mục có tên '" + segmentName + "' đã tồn tại.");
                    }
                    if (!Boolean.TRUE.equals(existing.getIsFolder())) {
                        throw new BadRequestException("Không thể tạo thư mục con bên trong '" + segmentName + "' vì đây là một tệp tin.");
                    }
                    currentParent = existing;
                    reusedCount++;
                    continue;
                }

                FsNode createdFolder = createSingleFolderInternal(
                        user,
                        currentParent,
                        segmentName,
                        bucketName,
                        storagePrefix,
                        createdObjectKeys
                );

                if (firstCreatedNodeId == null) {
                    firstCreatedNodeId = createdFolder.getId();
                }
                createdNodes.add(NodeResponse.from(createdFolder));
                currentParent = createdFolder;
            }

            NodeResponse leafResponse = !createdNodes.isEmpty()
                    ? createdNodes.get(createdNodes.size() - 1)
                    : NodeResponse.from(currentParent);

            return RecursiveFolderCreationResponse.builder()
                    .leafNode(leafResponse)
                    .firstCreatedNodeId(firstCreatedNodeId)
                    .createdCount(createdNodes.size())
                    .reusedCount(reusedCount)
                    .createdNodes(createdNodes)
                    .build();

        } catch (BadRequestException | ResourceNotFoundException e) {
            cleanupMinioObjects(bucketName, createdObjectKeys);
            throw e;
        } catch (Exception e) {
            log.error("Lỗi khi tạo thư mục đệ quy '{}' trên MinIO hoặc Database: {}", rawPath, e.getMessage(), e);
            cleanupMinioObjects(bucketName, createdObjectKeys);
            throw new RuntimeException("Khởi tạo thư mục đệ quy thất bại: " + e.getMessage(), e);
        }
    }

    private FsNode createSingleFolderInternal(
            FsUser user,
            FsNode parent,
            String folderName,
            String bucketName,
            String storagePrefix,
            List<String> createdObjectKeys
    ) {
        FsNode newFolder = FsNode.builder()
                .name(folderName)
                .parent(parent)
                .user(user)
                .nodeStatus(FsNode.Status.ACTIVE)
                .isFolder(true)
                .sizeBytes(0L)
                .build();

        newFolder = fsNodeRepository.save(newFolder);

        String newNodeId = String.valueOf(newFolder.getId());
        String parentNodePath = parent.getMaterializedPath();
        String newNodePath = NodePathUtilities.join(parentNodePath, newNodeId);
        newFolder.setMaterializedPath(newNodePath);

        MinioObjectResponse objectResponse = minioStorageService.createDirectory(bucketName, storagePrefix);
        if (objectResponse != null && objectResponse.getObjectKey() != null) {
            createdObjectKeys.add(objectResponse.getObjectKey());
            newFolder.setEtag(objectResponse.getEtag());
            newFolder.setS3ObjectKey(objectResponse.getObjectKey());
            newFolder.setStorageBucketName(objectResponse.getBucketName());
        }

        return fsNodeRepository.save(newFolder);
    }

    private List<String> parseAndValidateRecursivePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new BadRequestException("Đường dẫn thư mục không được để trống");
        }

        String normalized = rawPath.trim().replace('\\', '/');
        // Loại bỏ dấu '/' ở đầu và cuối chuỗi nếu có (ví dụ: "/A/B/C/" -> "A/B/C")
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }

        if (normalized.isEmpty()) {
            throw new BadRequestException("Đường dẫn thư mục không hợp lệ");
        }

        String[] rawTokens = normalized.split("/", -1);
        List<String> segments = new ArrayList<>(rawTokens.length);

        for (String token : rawTokens) {
            String segment = token.trim();
            if (segment.isEmpty()) {
                throw new BadRequestException("Đường dẫn thư mục không hợp lệ (chứa cấp thư mục rỗng '//')");
            }
            if (".".equals(segment) || "..".equals(segment)) {
                throw new BadRequestException("Tên thư mục không được phép là '.' hoặc '..'");
            }
            if (segment.length() > 255) {
                throw new BadRequestException("Tên thư mục '" + segment.substring(0, 20) + "...' vượt quá 255 ký tự");
            }
            segments.add(segment);
        }

        if (segments.size() > MAX_RECURSIVE_DEPTH) {
            throw new BadRequestException("Độ sâu thư mục đệ quy vượt quá giới hạn tối đa " + MAX_RECURSIVE_DEPTH + " cấp");
        }

        return segments;
    }

    private void cleanupMinioObjects(String bucketName, List<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return;
        }
        for (String key : objectKeys) {
            if (key != null && !key.isBlank()) {
                minioStorageService.deleteObjectSilently(bucketName, key);
            }
        }
    }
}
