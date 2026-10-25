package vn.edu.hust.kat.storageservice.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.common.api.exception.ResourceNotFoundException;
import vn.edu.hust.kat.storageservice.dto.response.NodeResponse;
import vn.edu.hust.kat.storageservice.dto.response.PagedResponse;
import vn.edu.hust.kat.storageservice.entity.FsNode;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.repository.FsNodeRepository;
import vn.edu.hust.kat.storageservice.utils.Constants;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
     * Lấy danh sách file/folder con (Duyệt thư mục).
     */
    @Transactional(readOnly = true)
    public PagedResponse<NodeResponse> getChildNodes(
            UUID userId,
            String parentIdStr,
            String nodeType,
            String name,
            String mimeType,
            int page,
            int size,
            List<String> sorts
    ) {
        if (userId == null) {
            throw new BadRequestException("User ID không hợp lệ");
        }

        // 1. Lấy và kiểm tra hợp lệ thư mục cha chỉ trong 1 truy vấn DB
        FsNode parentNode = resolveAndValidateParentNode(userId, parentIdStr);

        // 2. Chuẩn hóa các bộ lọc
        Boolean isFolder = parseNodeType(nodeType);
        String trimmedName = (name != null && !name.isBlank()) ? name.trim() : null;
        String trimmedMimeType = (mimeType != null && !mimeType.isBlank()) ? mimeType.trim() : null;

        // 3. Phân trang & Sắp xếp
        int pageNumber = Math.max(0, page);
        int pageSize = size > 0 ? size : 20;
        Pageable pageable = PageRequest.of(pageNumber, pageSize, parseSort(sorts));

        // 4. Truy vấn danh sách node con bằng ltree
        Page<FsNode> nodePage = fsNodeRepository.findChildNodesWithLtree(
                userId,
                parentNode.getMaterializedPath(),
                isFolder,
                trimmedName,
                trimmedMimeType,
                pageable
        );

        return PagedResponse.from(nodePage.map(NodeResponse::from));
    }

    private FsNode resolveAndValidateParentNode(UUID userId, String parentIdStr) {
        FsNode parentNode;

        if (isRootIdentifier(parentIdStr)) {
            log.info("Thư mục cha là thư mục gốc");

            parentNode = fsNodeRepository.findByUserIdAndParentIsNull(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Thư mục gốc không tồn tại"));
        } else {
            log.info("Thư mục cha không phải là thư mục gốc");
            Long parentId = parseParentId(parentIdStr);
            parentNode = fsNodeRepository.findById(parentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Thư mục cha không tồn tại"));

            // Kiểm tra quyền sở hữu ngay trên object thay vì query lại DB
            if (!userId.equals(parentNode.getUser().getId())) {
                throw new ResourceNotFoundException("Thư mục cha không tồn tại");
            }
        }

        if (isNodeDeleted(parentNode)) {
            log.info("Thư mục cha đã bị xóa");

            throw new ResourceNotFoundException("Thư mục cha không tồn tại");
        }

        if (!Boolean.TRUE.equals(parentNode.getIsFolder())) {
            throw new BadRequestException("Thư mục cha không hợp lệ");
        }

        return parentNode;
    }

    private boolean isRootIdentifier(String parentId) {
        return parentId == null
                || parentId.isBlank()
                || Constants.NULL.equalsIgnoreCase(parentId.trim())
                || Constants.STORAGE_ROOT_NAME.equalsIgnoreCase(parentId.trim());
    }

    private Long parseParentId(String parentIdStr) {
        String trimmed = parentIdStr.trim();
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            try {
                UUID.fromString(trimmed);
                throw new ResourceNotFoundException("Thư mục cha không tồn tại");
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Thư mục cha không hợp lệ");
            }
        }
    }

    private Boolean parseNodeType(String nodeType) {
        if (nodeType == null || nodeType.isBlank()) {
            return null;
        }
        return switch (nodeType.trim().toUpperCase()) {
            case "FOLDER" -> true;
            case "FILE" -> false;
            default -> null;
        };
    }

    public boolean isNodeDeleted(FsNode node) {
        return node.isDeleted() || node.getNodeStatus() == FsNode.Status.DELETED;
    }

    private Sort parseSort(List<String> sorts) {
        // Mặc định: FOLDER (is_folder = true) xếp trước FILE (is_folder = false) -> dùng DESC,
        // sau đó trong mỗi nhóm xếp theo tên tăng dần (name ASC).
        Sort defaultSort = Sort.by(
                Sort.Order.desc("is_folder"),
                Sort.Order.asc("name")
        );

        if (sorts == null || sorts.isEmpty()) {
            return defaultSort;
        }

        List<Sort.Order> orders = new ArrayList<>();
        // Luôn giữ ưu tiên gom nhóm FOLDER lên trước FILE
        orders.add(Sort.Order.desc("is_folder"));

        for (String part : sorts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            String[] tokens = part.trim().split(",");
            if (tokens.length == 0 || tokens[0].isBlank()) {
                continue;
            }

            String property = tokens[0].trim();
            // Bỏ qua nếu client truyền lại is_folder vì đã cố định ở đầu
            if ("is_folder".equalsIgnoreCase(property) || "isFolder".equalsIgnoreCase(property)) {
                continue;
            }

            Sort.Direction direction = (tokens.length > 1 && "desc".equalsIgnoreCase(tokens[1].trim()))
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;

            // Lưu ý: Vì dùng Native Query nên cần map ra tên cột trong Database (snake_case)
            String columnName = switch (property) {
                case "id" -> "id";
                case "parent_id", "parentId" -> "parent_id";
                case "node_status", "nodeStatus" -> "node_status";
                case "mime_type", "mimeType" -> "mime_type";
                case "size_bytes", "sizeBytes", "size" -> "size_bytes";
                case "etag" -> "etag";
                case "is_starred", "isStarred" -> "is_starred";
                case "created_at", "createdAt" -> "created_at";
                case "updated_at", "updatedAt" -> "updated_at";
                default -> "name";
            };

            orders.add(new Sort.Order(direction, columnName));
        }

        // Nếu chỉ có mỗi điều kiện is_folder (do sorts truyền vào rỗng/không hợp lệ), trả về defaultSort
        return orders.size() == 1 ? defaultSort : Sort.by(orders);
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
                .nodeStatus(FsNode.Status.ACTIVE)
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
                .nodeStatus(FsNode.Status.ACTIVE)
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
