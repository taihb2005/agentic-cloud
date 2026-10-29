package vn.edu.hust.kat.storageservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.common.api.exception.ResourceNotFoundException;
import vn.edu.hust.kat.common.api.response.BatchOperationResponse;
import vn.edu.hust.kat.common.api.response.OperationFailure;
import vn.edu.hust.kat.common.api.response.OperationResponse;
import vn.edu.hust.kat.storageservice.dto.response.*;
import vn.edu.hust.kat.storageservice.entity.FsNode;
import vn.edu.hust.kat.storageservice.kafka.event.NodePermanentDeleteEvent;
import vn.edu.hust.kat.storageservice.repository.FsNodeRepository;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FsNodeTrashService {

    public static final int ASYNC_THRESHOLD = 50;

    private final FsNodeRepository fsNodeRepository;
    private final FsNodeService fsNodeService;
    private final MinioStorageService minioStorageService;
    private final OutboxService outboxService;

    /**
     * Lấy danh sách các node trong thùng rác có phân trang và bộ lọc.
     */
    @Transactional(readOnly = true)
    public PagedResponse<NodeResponse> getTrashNodes(
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

        Long parentId = null;
        if (parentIdStr != null && !parentIdStr.isBlank() && !"null".equalsIgnoreCase(parentIdStr.trim())) {
            parentId = fsNodeService.parseNodeId(parentIdStr);
        }

        Boolean isFolder = parseNodeType(nodeType);
        String trimmedName = (name != null && !name.isBlank()) ? name.trim() : null;
        String trimmedMimeType = (mimeType != null && !mimeType.isBlank()) ? mimeType.trim() : null;

        int pageNumber = Math.max(0, page);
        int pageSize = size > 0 ? size : 20;
        Pageable pageable = PageRequest.of(pageNumber, pageSize, parseTrashSort(sorts));

        Page<FsNode> trashPage = fsNodeRepository.findTrashNodes(
                userId,
                parentId,
                isFolder,
                trimmedName,
                trimmedMimeType,
                pageable
        );

        return PagedResponse.from(trashPage.map(NodeResponse::from));
    }

    /**
     * Khôi phục danh sách node từ thùng rác.
     * Nếu node là thư mục, toàn bộ subtree thuộc cùng delete batch sẽ được khôi phục.
     * Nếu thư mục cha đã bị xóa, node sẽ được đưa về thư mục gốc.
     * @param userId người dùng
     * @param nodeIds Danh sách các node cần khôi phục từ thùng rác
     */
    @Transactional
    public BatchOperationResponse<RestoreResult, NodeFailureResult> restoreNodes(
            UUID userId,
            List<String> nodeIds
    ) {
        if (userId == null) {
            throw new BadRequestException("User ID không hợp lệ");
        }

        if (nodeIds == null || nodeIds.isEmpty()) {
            throw new BadRequestException("Danh sách node cần khôi phục không được để trống");
        }

        int total = nodeIds.size();

        List<RestoreResult> succeeded = new ArrayList<>();
        List<OperationFailure<NodeFailureResult>> failed = new ArrayList<>();

        nodeIds.forEach(nodeIdStr -> {
            try {
                Long nodeId = fsNodeService.parseNodeId(nodeIdStr);

                FsNode node = fsNodeRepository
                        .findTrashNodeByIdAndUserId(nodeId, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Node không tồn tại trong thùng rác"));

                // Nếu node nằm trong thùng rác chắc chắn sẽ có batchDeleteId
                UUID deleteBatchId = node.getDeleteBatchId();

                if (deleteBatchId == null) {
                    deleteBatchId = UUID.randomUUID();
                }

                // Tìm thư mục mà nó khôi phục về
                FsNode targetParent;
                boolean needRebaseToRoot = false;

                // Thư mục cha bị xóa thì sẽ chuyển về thư mục root
                if (node.getParent() == null) {

                    targetParent = fsNodeRepository
                            .findByUserIdAndParentIsNull(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("Thư mục gốc không tồn tại"));

                } else if (fsNodeService.isNodeDeleted(node.getParent())) {
                    // targetParent sẽ là root do cha của node cần khôi phục đã bị xóa
                    targetParent = fsNodeRepository
                            .findByUserIdAndParentIsNull(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("Thư mục gốc không tồn tại"));

                    needRebaseToRoot = true;
                    log.info("Parent của node {} đã bị xóa, node sẽ được đưa về root", nodeId);

                } else {
                    targetParent = node.getParent();
                }

                Long targetParentId = targetParent.getId();

                // Kiểm tra trùng tên trong thư mục khôi phục
                boolean duplicated = fsNodeRepository.existsActiveNodeByParentAndName(userId, targetParentId, node.getName(), nodeId);

                // Tạm thời ngăn không cho khôi phục về
                // TODO(Triển khai thuật toán rename nếu trùng)
                if (duplicated) {

                    String folderDesc = targetParent.getParent() == null ? "thư mục gốc" : "thư mục cha";

                    NodeFailureResult failureData = NodeFailureResult.builder()
                                    .nodeId(nodeIdStr)
                                    .build();

                    failed.add(OperationFailure.nameAlreadyExists()
                                    .body("Đã tồn tại file hoặc thư mục cùng tên '" + node.getName() + "' trong " + folderDesc, failureData));

                    return;
                }

                // Khôi phục node về thư mục chỉ định
                String oldPath = node.getMaterializedPath();

                int affected;

                if (Boolean.TRUE.equals(node.getIsFolder())) {
                    affected = fsNodeRepository.restoreFolderAndDescendants(userId, oldPath, deleteBatchId);
                    log.info("Khôi phục thư mục thành công, nodeId={}, có {} dòng bị ảnh hưởng", nodeId, affected);
                } else {
                    affected = fsNodeRepository.restoreFile(userId, nodeId, deleteBatchId);
                    log.info("Khôi phục file thành công, nodeId={}, có {} dòng bị ảnh hưởng", nodeId, affected);
                }

                // Restore thất bại
                if (affected <= 0) {

                    NodeFailureResult failureData = NodeFailureResult.builder()
                                    .nodeId(nodeIdStr)
                                    .build();

                    failed.add(OperationFailure.invalidOperation()
                                    .body("Không thể khôi phục node", failureData));

                    return;
                }

                // Trường hợp cần rebase về root
                if (needRebaseToRoot) {

                    if (node.getIsFolder()) {

                        fsNodeRepository.moveFolderAndDescendents(nodeId, targetParentId, oldPath, targetParent.getMaterializedPath());

                    } else {
                        fsNodeRepository.moveFile(nodeId, targetParentId, targetParent.getMaterializedPath());
                    }
                }

                // Lấy node sau khi đã khôi phục về
                FsNode updatedNode = fsNodeRepository.findById(nodeId)
                                .orElse(node);

                String parentIdResult = updatedNode.getParent() != null
                                && updatedNode.getParent().getParent() != null
                                ? updatedNode.getParent().getId().toString()
                                : (updatedNode.getParent() != null
                                        && !needRebaseToRoot
                                        ? updatedNode.getParent()
                                        .getId()
                                        .toString()
                                        : null);

                succeeded.add(RestoreResult.builder()
                                .id(nodeIdStr)
                                .parentId(parentIdResult)
                                .name(updatedNode.getName())
                                .isFolder(updatedNode.getIsFolder())
                                .nodeStatus(updatedNode.getNodeStatus() != null ? updatedNode.getNodeStatus().toString() : "ACTIVE")
                                .restoredCount(Boolean.TRUE.equals(updatedNode.getIsFolder()) ? affected : null)
                                .build()
                );

            } catch (ResourceNotFoundException e) {

                NodeFailureResult failureData = NodeFailureResult.builder()
                                .nodeId(nodeIdStr)
                                .build();

                failed.add(OperationFailure.nodeNotFound()
                                .body("Node không tồn tại trong thùng rác", failureData));

            } catch (BadRequestException e) {

                NodeFailureResult failureData = NodeFailureResult.builder()
                                .nodeId(nodeIdStr)
                                .build();

                failed.add(OperationFailure.badRequest()
                                .body(e.getMessage(), failureData));

            } catch (Exception e) {

                log.error("Khôi phục node {} thất bại", nodeIdStr, e);

                NodeFailureResult failureData = NodeFailureResult.builder()
                                .nodeId(nodeIdStr)
                                .build();

                failed.add(OperationFailure
                                .internalError()
                                .body(e.getMessage() != null ? e.getMessage() : "Không thể khôi phục node", failureData));
            }
        });

        return BatchOperationResponse.of(total, succeeded, failed);
    }

    /**
     * Xóa vĩnh viễn node theo list bằng Kafka và outbox
     * @param userId người dùng
     * @param nodeIds Danh sách các node cần xóa vĩnh viễn
     */
    @Transactional
    public BatchOperationResponse<DeleteResult, NodeFailureResult> permanentDeleteTrashSync(UUID userId, List<String> nodeIds) {
        if (userId == null) {
            throw new BadRequestException("User ID không hợp lệ");
        }

        int total = nodeIds.size();
        List<DeleteResult> succeeded = new ArrayList<>();
        List<OperationFailure<NodeFailureResult>> failed = new ArrayList<>();

        nodeIds.forEach(nodeIdStr -> {
            try {
                FsNode node = fsNodeService.resolveAndValidateTrashNode(userId, nodeIdStr);

                UUID operationId = UUID.randomUUID();
                List<FsNode> nodesToDelete = fsNodeRepository.findDescendantsIncludingSelfInTrash(userId, node.getMaterializedPath());

                for (FsNode n : nodesToDelete) {
                    if (!node.getIsFolder() && n.getStorageBucketName() != null && n.getS3ObjectKey() != null) {
                        try {
                            minioStorageService.deleteObject(n.getStorageBucketName(), n.getS3ObjectKey());
                        } catch (Exception ex) {
                            log.warn("Không thể xóa object MinIO key={}", n.getS3ObjectKey(), ex);
                        }
                    }
                }

                List<Long> idsToDelete = nodesToDelete.stream().map(FsNode::getId).toList();
                fsNodeRepository.deletePermanent(userId, idsToDelete);

                succeeded.add(DeleteResult.builder()
                        .nodeId(nodeIdStr)
                        .operationId(operationId.toString())
                        .build());

            } catch (ResourceNotFoundException e) {
                NodeFailureResult failureResult = NodeFailureResult.builder().nodeId(nodeIdStr).build();
                failed.add(OperationFailure.nodeNotFound()
                        .body("Node không tồn tại", failureResult));
            } catch (Exception e) {
                log.error("Xóa vĩnh viễn node {} thất bại", nodeIdStr, e);

                NodeFailureResult failureResult = NodeFailureResult.builder().nodeId(nodeIdStr).build();
                failed.add(OperationFailure.invalidOperation()
                        .body(e.getMessage(), failureResult));
            }
        });

        return BatchOperationResponse.of(total, succeeded, failed);
    }

    /**
     * Xóa vĩnh viễn bất đồng bộ qua Outbox -> Kafka -> MinIO Worker.
     * Áp dụng khi xóa toàn bộ thùng rác
     */
    @Transactional
    public BatchOperationResponse<Void, NodeFailureResult> permanentDeleteTrashAsync(UUID userId, List<String> nodeIds) {
        if (userId == null) {
            throw new BadRequestException("User ID không hợp lệ");
        }

        if (nodeIds == null) {
            throw new BadRequestException("Danh sách node cần xóa không hợp lệ");
        }

        UUID operationId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        int total = nodeIds.size();
        List<Void> succeeded = new ArrayList<>();
        List<OperationFailure<NodeFailureResult>> failed = new ArrayList<>();

        // Gom tất cả các node con thuộc user nếu có folder
        Set<Long> allNodeIdsToDelete = new HashSet<>();
        List<NodePermanentDeleteEvent.DeleteObject> deleteObjects = new ArrayList<>();

        nodeIds.forEach(nodeIdStr -> {
            NodeFailureResult failureResult = NodeFailureResult.builder().nodeId(nodeIdStr).build();

            try {
                // Kiểm tra tính hợp lệ của node trong thùng rác
                FsNode node = fsNodeService.resolveAndValidateTrashNode(userId, nodeIdStr);

                List<FsNode> descendants = fsNodeRepository.findDescendantsIncludingSelfInTrash(userId, node.getMaterializedPath());
                for (FsNode d : descendants) {
                    allNodeIdsToDelete.add(d.getId());

                    deleteObjects.add(NodePermanentDeleteEvent.DeleteObject.builder()
                            .nodeId(d.getId())
                            .bucketName(d.getStorageBucketName())
                            .objectKey(d.getS3ObjectKey())
                            .size(d.getSizeBytes())
                            .build());

                    log.info("Các node sẽ bị xóa, kèm objects {} {}", allNodeIdsToDelete, deleteObjects);
                }
            } catch (ResourceNotFoundException e) {
                failed.add(OperationFailure.nodeNotFound().body(e.getMessage(), failureResult));
            } catch (BadRequestException e) {
                failed.add(OperationFailure.badRequest().body(e.getMessage(), failureResult));
            } catch (Exception e) {
                failed.add(OperationFailure.internalError().body(e.getMessage(), failureResult));
            }
        });

        if (!deleteObjects.isEmpty()) {
            NodePermanentDeleteEvent event = new NodePermanentDeleteEvent(
                    eventId,
                    operationId,
                    userId,
                    deleteObjects
            );
            outboxService.createNodePermanentDeleteEvent(event);
        }

        return BatchOperationResponse.of(total, succeeded, failed);
    }

    private Sort parseTrashSort(List<String> sorts) {
        Sort defaultSort = Sort.by(
                Sort.Order.desc("is_folder"),
                Sort.Order.desc("deleted_at"),
                Sort.Order.asc("name")
        );

        if (sorts == null || sorts.isEmpty()) {
            return defaultSort;
        }

        List<Sort.Order> orders = new ArrayList<>();
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
            if ("is_folder".equalsIgnoreCase(property) || "isFolder".equalsIgnoreCase(property)) {
                continue;
            }

            Sort.Direction direction = (tokens.length > 1 && "desc".equalsIgnoreCase(tokens[1].trim()))
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;

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

        return orders.size() == 1 ? defaultSort : Sort.by(orders);
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
}
