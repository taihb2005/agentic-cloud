package vn.edu.hust.kat.storageservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Node;
import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.common.api.exception.ResourceNotFoundException;
import vn.edu.hust.kat.common.api.response.BatchOperationResponse;
import vn.edu.hust.kat.common.api.response.FailureCode;
import vn.edu.hust.kat.common.api.response.OperationFailure;
import vn.edu.hust.kat.storageservice.dto.request.MoveNodeRequest;
import vn.edu.hust.kat.storageservice.dto.request.UpdateNodeRequest;
import vn.edu.hust.kat.storageservice.dto.response.*;
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

    @Transactional
    public NodeResponse updateNode(UUID userId, String nodeIdStr, UpdateNodeRequest request) {
        if (userId == null) {
            throw new BadRequestException("User id không hợp lệ");
        }

        FsNode node = resolveAndValidateNode(userId, nodeIdStr, true);

        node.setName(request.getName());
        node.setIsStarred(request.isStarred());

        return NodeResponse.from(node);
    }
    /**
     * Di chuyển danh sách node
     */
    @Transactional
    public BatchOperationResponse<MoveResult, NodeFailureResult> moveNodes(UUID userId, MoveNodeRequest request)
    {
        if (userId == null) {
            throw new BadRequestException("User ID không hợp lệ");
        }

        if (request == null || request.getSrcNodeIds() == null || request.getSrcNodeIds().isEmpty()) {
            throw new BadRequestException("Danh sách node cần di chuyển không được để trống");
        }

        List<String> srcNodeIds = request.getSrcNodeIds();

        int total = srcNodeIds.size();

        List<MoveResult> succeeded = new ArrayList<>();
        List<OperationFailure<NodeFailureResult>> failed = new ArrayList<>();

        /*
         * ============================================================
         * Xác định thư mục đích
         * ============================================================
         */
        FsNode dstNode = null;

        FailureCode dstFailureCode = null;
        String dstFailureMessage = null;

        try {
            dstNode = resolveAndValidateFolder(userId, request.getDstNodeId(), true);

        } catch (ResourceNotFoundException e) {

            dstFailureCode = FailureCode.DESTINATION_NOT_FOUND;
            dstFailureMessage = "Thư mục đích không tồn tại";

        } catch (BadRequestException e) {

            dstFailureCode = FailureCode.INVALID_DESTINATION;
            dstFailureMessage = e.getMessage();

        } catch (Exception e) {

            log.error("Không thể xác định thư mục đích {}", request.getDstNodeId(), e);

            dstFailureCode = FailureCode.DESTINATION_ERROR;
            dstFailureMessage = e.getMessage() != null ? e.getMessage() : "Không thể xác định thư mục đích";
        }

        /*
         * Nếu destination không hợp lệ:
         * tất cả source node đều thất bại với cùng một destination error.
         */
        if (dstNode == null) {

            for (String srcId : srcNodeIds) {

                NodeFailureResult failureData = NodeFailureResult.builder()
                                .nodeId(srcId)
                                .build();

                failed.add(OperationFailure.of(dstFailureCode, dstFailureMessage, failureData));
            }

            return BatchOperationResponse.of(total, succeeded, failed);
        }

        Long dstNodeId = dstNode.getId();
        String dstNodePath = dstNode.getMaterializedPath();

        String resultParentId =
                dstNode.getParent() == null
                        && request.getDstNodeId() == null
                        ? null
                        : request.getDstNodeId() != null
                        ? request.getDstNodeId()
                        : dstNode.getId().toString();

        /*
         * ============================================================
         * Xử lý từng source node
         * ============================================================
         */
        for (String srcNodeIdStr : srcNodeIds) {

            try {
                FsNode srcNode = resolveAndValidateNode(userId, srcNodeIdStr, true);

                NodeFailureResult failureData = NodeFailureResult.builder().nodeId(srcNodeIdStr).build();

                /*
                 * Không cho phép di chuyển root
                 */
                if (srcNode.getParent() == null || Constants.STORAGE_ROOT_NAME.equalsIgnoreCase(srcNode.getName())) {

                    failed.add(OperationFailure.invalidOperation()
                                    .body("Không thể di chuyển thư mục gốc", failureData));

                    continue;
                }

                Long srcNodeId = srcNode.getId();
                String srcNodePath = srcNode.getMaterializedPath();

                /*
                 * Không được di chuyển node vào chính nó
                 * hoặc vào descendant của chính nó.
                 */
                if (srcNodeId.equals(dstNodeId) || fsNodeRepository.isAncestor(srcNodeId, dstNodeId))
                {

                    failed.add(OperationFailure.invalidDestination()
                                    .body("Thư mục đích không được phép là " + "con của thư mục nguồn", failureData));

                    continue;
                }

                /*
                 * Kiểm tra trùng tên
                 */
                boolean duplicated = fsNodeRepository.existsActiveNodeByParentAndName(userId, dstNodeId, srcNode.getName(), srcNodeId);

                if (duplicated) {

                    failed.add(OperationFailure.nameAlreadyExists()
                                    .body("Đã tồn tại file hoặc thư mục " + "cùng tên trong thư mục đích", failureData)
                    );

                    continue;
                }

                /*
                 * Thực hiện move
                 */
                if (Boolean.TRUE.equals(srcNode.getIsFolder())) {

                    fsNodeRepository.moveFolderAndDescendents(srcNodeId, dstNodeId, srcNodePath, dstNodePath);

                } else {

                    fsNodeRepository.moveFile(srcNodeId, dstNodeId, dstNodePath);
                }

                /*
                 * Thành công
                 */
                succeeded.add(MoveResult.builder()
                                .nodeId(srcNodeIdStr)
                                .parentId(resultParentId)
                                .build());

            } catch (ResourceNotFoundException e) {

                NodeFailureResult failureData = NodeFailureResult.builder()
                                .nodeId(srcNodeIdStr)
                                .build();

                failed.add(
                        OperationFailure
                                .nodeNotFound()
                                .body("Node không tồn tại", failureData));

            } catch (BadRequestException e) {

                NodeFailureResult failureData = NodeFailureResult.builder()
                                .nodeId(srcNodeIdStr)
                                .build();

                failed.add(OperationFailure.badRequest()
                                .body(e.getMessage(), failureData));

            } catch (Exception e) {

                log.error("Di chuyển node {} thất bại", srcNodeIdStr, e);

                NodeFailureResult failureData = NodeFailureResult.builder()
                                .nodeId(srcNodeIdStr)
                                .build();

                failed.add(OperationFailure
                                .moveFailed()
                                .body(e.getMessage() != null ? e.getMessage() : "Di chuyển node thất bại", failureData)
                );
            }
        }

        return BatchOperationResponse.of(total, succeeded, failed);
    }

    /**
     * Di chuyển một node đơn lẻ.
     */
    @Transactional
    public NodeResponse moveNode(UUID userId, String srcNodeIdStr, boolean needSrcActive, String dstNodeIdStr, boolean needDstActive) {
        if (userId == null) {
            throw new BadRequestException("User ID không hợp lệ");
        }

        FsNode srcNode = resolveAndValidateNode(userId, srcNodeIdStr, needSrcActive);
        FsNode dstNode = resolveAndValidateFolder(userId, dstNodeIdStr, needDstActive);
        Long srcNodeId = srcNode.getId();
        Long dstNodeId = dstNode.getId();
        String srcNodePath = srcNode.getMaterializedPath();
        String dstNodePath = dstNode.getMaterializedPath();

        boolean isValid = !fsNodeRepository.isAncestor(srcNodeId, dstNodeId) && !srcNodeId.equals(dstNodeId);
        if (!isValid) {
            throw new BadRequestException("Thư mục đích tới không được phép là con của thư mục nguồn");
        }

        try {
            if (Boolean.TRUE.equals(srcNode.getIsFolder())) {
                int affected = fsNodeRepository.moveFolderAndDescendents(srcNodeId, dstNodeId, srcNodePath, dstNodePath);
                log.info("Di chuyển thư mục từ {} đến {} thành công, có {} dòng ảnh hưởng", srcNodeId, dstNodeId, affected);
            } else {
                int affected = fsNodeRepository.moveFile(srcNodeId, dstNodeId, dstNodePath);
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
     * Đưa danh sách node vào thùng rác (Xóa mềm).
     */
    @Transactional
    public BatchOperationResponse<DeleteResult, NodeFailureResult> softDeleteNodes(UUID userId, List<String> nodeIds) {
        if (userId == null) {
            throw new BadRequestException("User ID không hợp lệ");
        }

        if (nodeIds == null || nodeIds.isEmpty()) {
            throw new BadRequestException("Danh sách node cần xóa không được để trống");
        }

        int total = nodeIds.size();
        List<DeleteResult> succeeded = new ArrayList<>();
        List<OperationFailure<NodeFailureResult>> failed = new ArrayList<>();

        nodeIds.forEach(nodeIdStr -> {
            NodeFailureResult failureResult = NodeFailureResult.builder()
                    .nodeId(nodeIdStr)
                    .build();

            try {
                FsNode node = resolveAndValidateNode(userId, nodeIdStr, true);

                if (node.getParent() == null || Constants.STORAGE_ROOT_NAME.equalsIgnoreCase(node.getName())) {
                    failed.add(OperationFailure.invalidOperation()
                            .body("Không thể xóa thư mục gốc", failureResult));
                    return;
                }

                Long nodeId = node.getId();
                String nodePath = node.getMaterializedPath();
                UUID deleteBatchId = UUID.randomUUID();

                int affected;
                if (Boolean.TRUE.equals(node.getIsFolder())) {
                    affected = fsNodeRepository.softDeleteFolderAndDescendants(userId, nodePath, deleteBatchId);
                    log.info("Di chuyển thư mục {} ra thùng rác thành công, có {} dòng bị ảnh hưởng", nodeId, affected);
                } else {
                    affected = fsNodeRepository.softDeleteFile(userId, nodeId, deleteBatchId);
                    log.info("Di chuyển node {} ra thùng rác thành công, có {} dòng bị ảnh hưởng", nodeId, affected);
                }

                if (affected > 0) {
                    succeeded.add(DeleteResult.builder()
                            .nodeId(nodeIdStr)
                            .operationId(deleteBatchId.toString())
                            .build());
                } else {
                    failed.add(OperationFailure.nodeNotFound()
                            .body("Node không tồn tại hoặc đã bị xóa", failureResult));
                }
            } catch (ResourceNotFoundException e) {
                failed.add(OperationFailure.invalidOperation()
                        .body("Node không tồn tại", failureResult));
            } catch (BadRequestException e) {
                failed.add(OperationFailure.badRequest()
                        .body(e.getMessage(), failureResult));
            } catch (Exception e) {
                log.error("Xóa mềm node {} thất bại", nodeIdStr, e);
                failed.add(OperationFailure.invalidOperation()
                        .body(e.getMessage(), failureResult));
            }
        });

        return BatchOperationResponse.of(total, succeeded, failed);
    }

    /**
     * Đưa một node vào thùng rác (hỗ trợ gọi đơn lẻ).
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
            if (Boolean.TRUE.equals(node.getIsFolder())) {
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

    @Transactional
    public void deleteNode(Long id) {
        fsNodeRepository.deleteById(id);
    }

    public FsNode resolveAndValidateFolder(UUID userId, String folderIdStr, boolean needActiveNode) {
        FsNode folderNode = resolveAndValidateNode(userId, folderIdStr, needActiveNode);
        if (!Boolean.TRUE.equals(folderNode.getIsFolder())) {
            throw new BadRequestException("Thư mục không hợp lệ");
        }
        return folderNode;
    }

    public FsNode resolveAndValidateTrashNode(UUID userId, String nodeIdStr) {
        Long nodeId = Long.parseLong(nodeIdStr);

        return fsNodeRepository.findTrashNodeByIdAndUserId(nodeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Node không tồn tại"));
    }


    public FsNode resolveAndValidateNode(UUID userId, String nodeIdStr, boolean needActiveNode) {
        FsNode node;

        if (isRootIdentifier(nodeIdStr)) {
            log.info("Node là thư mục gốc");

            node = fsNodeRepository.findByUserIdAndParentIsNull(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Node gốc không tồn tại"));
        } else {
            log.info("Node không phải là thư mục gốc: {}", nodeIdStr);
            Long nodeId = parseNodeId(nodeIdStr);
            node = fsNodeRepository.findById(nodeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Node không tồn tại"));

            // Kiểm tra quyền sở hữu ngay trên object
            if (!userId.equals(node.getUser().getId())) {
                throw new ResourceNotFoundException("Node không tồn tại");
            }
        }

        if (isNodeDeleted(node) && needActiveNode) {
            log.info("Node đã bị xóa");
            throw new ResourceNotFoundException("Node không tồn tại");
        }

        return node;
    }

    private boolean isRootIdentifier(String parentId) {
        return parentId == null
                || parentId.isBlank()
                || Constants.NULL.equalsIgnoreCase(parentId.trim())
                || Constants.STORAGE_ROOT_NAME.equalsIgnoreCase(parentId.trim());
    }

    private Long parseParentId(String parentIdStr) {
        if (parentIdStr == null || parentIdStr.isBlank()) {
            throw new BadRequestException("Thư mục cha không hợp lệ");
        }
        String trimmed = parentIdStr.trim();
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            throw new ResourceNotFoundException("Thư mục cha không tồn tại");
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
            throw new ResourceNotFoundException("Node không tồn tại");
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
                case "deleted_at", "deletedAt" -> "deleted_at";
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
