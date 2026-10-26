package vn.edu.hust.kat.storageservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

        // 1. Lấy và kiểm tra hợp lệ thư mục cha
        FsNode parentNode = resolveAndValidateNode(userId, parentIdStr, true);

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

    /**
     * Lấy metadata của node trên minio
     * @param userId user id của người dùng
     * @param nodeId id của node đang muốn lấy metadata
     */
    @Transactional(readOnly = true)
    public NodeResponse getNodeMetadata(UUID userId, String nodeId) {
        if (userId == null) {
            throw new BadRequestException("User id không hợp lệ");
        }

        // Kiểm tra tính hợp lệ của node id người dùng gủi lên
        FsNode node = resolveAndValidateNode(userId, nodeId, true);

        return NodeResponse.from(node);
    }

    /**
     * Di chuyển node
     * @param srcNodeIdStr Node id của node gốc cần di chuyển
     * @param needSrcActive Node id của node nguồn có cần active không
     * @param dstNodeIdStr Node id của node cần di chuyển đến
     * @param needDstActive Node id của node đích có cần active không
     */
    @Transactional
    public NodeResponse moveNode(UUID userId, String srcNodeIdStr, boolean needSrcActive, String dstNodeIdStr, boolean needDstActive) {
        if (userId == null) {
            throw new BadRequestException("User ID không hợp lệ");
        }

        FsNode srcNode = resolveAndValidateNode(userId, srcNodeIdStr, needSrcActive);
        FsNode dstNode = resolveAndValidateNode(userId, dstNodeIdStr, needDstActive);
        Long srcNodeId = srcNode.getId();
        Long dstNodeId = dstNode.getId();
        String srcNodePath = srcNode.getMaterializedPath();
        String dstNodePath = dstNode.getMaterializedPath();

        boolean isValid = !fsNodeRepository.isAncestor(srcNodeId, dstNodeId) && !srcNodeId.equals(dstNodeId);
        if (!isValid) {
            throw new BadRequestException("Thư mục đích tới không được phép là con của thư mục nguồn");
        }

        try {
            if (srcNode.getIsFolder()) {
                int affected = fsNodeRepository.moveFolderAndDescendents(srcNodeId, dstNodeId, srcNodePath, dstNodePath);
                log.info("Di chuyển thư mục từ {} đến {} thành công, có {} dòng ảnh hưởng", srcNodeId, dstNodeId, affected);
            } else {
                int affected = fsNodeRepository.moveFile(srcNodeId, dstNodeId, srcNodePath);
                log.info("Di chuyển file từ {} đến {} thành công, có {} dòng ảnh hưởng", srcNodeId, dstNodeId, affected);
            }

            FsNode updatedNode = fsNodeRepository.findById(srcNodeId).orElseThrow(
                    () -> new RuntimeException("Không thể di chuyển node")
            );

            return NodeResponse.from(updatedNode);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new BadRequestException(e.getMessage());
        }
    }

    /**
     * Đưa node vào thùng rác. Nếu là thư mục sẽ xóa đệ quy bằng ltree.
     */
    @Transactional
    public void softDeleteNode(UUID userId, String nodeIdStr) {
        if (userId == null) {
            throw new BadRequestException("User ID không hợp lệ");
        }

        FsNode node = resolveAndValidateNode(userId, nodeIdStr, true);

        if (node.getParent() == null || Constants.STORAGE_ROOT_NAME.equalsIgnoreCase(node.getName())) {
            throw new BadRequestException("Không thể xóa thư mục gốc");
        }

        Long nodeId = node.getId();
        String nodePath = node.getMaterializedPath();
        UUID deleteBatchId = UUID.randomUUID();

        try {
            if (node.getIsFolder()) {
                int affected = fsNodeRepository.softDeleteFolderAndDescendants(userId, nodePath, deleteBatchId);
                log.info("Di chuyển thư mục {} ra thùng rác thành công, có {} dòng bị ảnh hưởng", nodeId, affected);
            } else {
                int affected = fsNodeRepository.softDeleteFile(userId, nodeId, deleteBatchId);
                log.info("Di chuyển node {} ra thùng rác thành công, có {} dòng bị ảnh hưởng", nodeId, affected);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new BadRequestException(e.getMessage());
        }
    }


    /**
     * Khôi phục node đã xóa từ thùng rác.
     * Nếu node là thư mục, toàn bộ subtree thuộc cùng delete batch
     * sẽ được khôi phục.
     * Nếu thư mục cha đã bị xóa, node sẽ được đưa về thư mục gốc.
     */
    @Transactional
    public NodeResponse restoreNode(UUID userId, String nodeIdStr) {
        if (userId == null) {
            throw new BadRequestException("User ID không hợp lệ");
        }


        FsNode node = resolveAndValidateNode(userId, nodeIdStr, false);
        Long nodeId = node.getId();

        if (!isNodeDeleted(node)) {
            throw new BadRequestException("Không thể khôi phục lại node chưa bị xóa");
        }

        UUID deleteBatchId = node.getDeleteBatchId();

        if (deleteBatchId == null) {
            throw new BadRequestException("Không tìm thấy thông tin deletion batch của node");
        }

        // Xác định thư mục cha sau khi restore.
        FsNode targetParent;
        boolean needRebaseToRoot = false;

        if (node.getParent() == null) {
            targetParent = fsNodeRepository
                    .findByUserIdAndParentIsNull(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Thư mục gốc không tồn tại"));
        } else if (isNodeDeleted(node.getParent())) {
            // Parent thực sự của node đã bị xóa → đưa node về Root
            targetParent = fsNodeRepository
                    .findByUserIdAndParentIsNull(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Thư mục gốc không tồn tại"));
            needRebaseToRoot = true;

            log.info("Parent của node {} đã bị xóa, node sẽ được đưa về root", nodeId);
        } else {
            // Parent vẫn tồn tại và đang ACTIVE thì node sẽ được đưa về cha của nó
            targetParent = node.getParent();
        }

        Long targetParentId = targetParent.getId();

        // Kiểm tra trùng lặp
        boolean duplicated = fsNodeRepository.existsActiveNodeByParentAndName(userId, targetParentId, node.getName(), nodeId);

        if (duplicated) {
            String folderDesc = targetParent.getParent() == null ? "thư mục gốc" : "thư mục cha";

            throw new BadRequestException("Đã tồn tại file hoặc thư mục cùng tên '" + node.getName() + "' trong " + folderDesc);
        }

        /*
         * Path mới của node.
         *
         * Nếu parent không bị xóa:
         *
         *     parent.path + node.id
         *
         * Nếu parent đã bị xóa:
         *
         *     root.path + node.id
         */
        String oldPath = node.getMaterializedPath();

        int affected;

        if (node.getIsFolder()) {
            affected = fsNodeRepository.restoreFolderAndDescendants(userId, oldPath, deleteBatchId);
            log.info("Khôi phục thư mục thành công, nodeId={}, có {} dòng bị ảnh hưởng", nodeId, affected);
        } else {
            affected = fsNodeRepository.restoreFile(userId,nodeId, deleteBatchId);
            log.info("Khôi phục file thành công, nodeId={}, có {} dòng bị ảnh hưởng", nodeId, affected);
        }

        if (affected <= 0) {
            throw new BadRequestException("Không thể khôi phục node");
        }

        if (needRebaseToRoot) {
            moveNode(userId, nodeIdStr, false, targetParentId.toString(), true);
        }

        FsNode updatedNode = fsNodeRepository.findById(nodeId).orElseThrow(
                () -> new RuntimeException("Không thể khôi phục node")
        );

        return NodeResponse.from(updatedNode);
    }

    public FsNode resolveAndValidateNode(UUID userId, String nodeIdStr, boolean needActiveNode) {
        FsNode parentNode;

        if (isRootIdentifier(nodeIdStr)) {
            log.info("Node cha là thư mục gốc");

            parentNode = fsNodeRepository.findByUserIdAndParentIsNull(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Node gốc không tồn tại"));
        } else {
            log.info("Node không phải là thư mục gốc");
            Long parentId = parseParentId(nodeIdStr);
            parentNode = fsNodeRepository.findById(parentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Node không tồn tại"));

            // Kiểm tra quyền sở hữu ngay trên object thay vì query lại DB
            if (!userId.equals(parentNode.getUser().getId())) {
                throw new ResourceNotFoundException("Node không tồn tại");
            }
        }

        if (isNodeDeleted(parentNode) && needActiveNode) {
            log.info("Thư mục cha đã bị xóa");

            throw new ResourceNotFoundException("Thư mục không tồn tại");
        }

        if (!Boolean.TRUE.equals(parentNode.getIsFolder())) {
            throw new BadRequestException("Thư mục không hợp lệ");
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

    public Long parseNodeId(String nodeIdStr) {
        if (nodeIdStr == null || nodeIdStr.isBlank()) {
            throw new BadRequestException("Node ID không hợp lệ");
        }
        String trimmed = nodeIdStr.trim();
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            try {
                UUID.fromString(trimmed);
                throw new ResourceNotFoundException("Node không tồn tại");
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Node ID không hợp lệ");
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
        return (node.isDeleted() || node.getNodeStatus() == FsNode.Status.DELETED) &&
                node.getDeleteBatchId() != null;
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
