package vn.edu.hust.kat.agentservice.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.hust.kat.agentservice.context.ChatTurnContext;
import vn.edu.hust.kat.agentservice.context.UserContextManager;
import vn.edu.hust.kat.agentservice.grpc.StorageGrpcClient;
import vn.edu.hust.kat.agentservice.guardrail.annotation.SafeAction;
import vn.edu.hust.kat.agentservice.guardrail.snapshot.body.InverseActionType;
import vn.edu.hust.kat.agentservice.guardrail.snapshot.payload.NodeMutationUndoSnapshot;
import vn.edu.hust.kat.agentservice.guardrail.snapshot.payload.UndoSnapshotPayload;
import vn.edu.hust.kat.agentservice.guardrail.undo.Undoable;
import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.proto.storage.v1.NodeItemResponse;
import vn.edu.hust.kat.proto.storage.v1.SoftDeleteNodeGrpcResponse;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class StorageSafeTools implements ITools, Undoable {

    private static final Set<String> UNDOABLE_ACTIONS = Set.of(
            "RENAME", "MOVE", "STAR", "SOFT_DELETE", "CREATE_FOLDER"
    );
    private static final Pattern CREATED_NODE_ID_PATTERN = Pattern.compile("với ID:\\s*(\\S+)");
    private static final Pattern DELETE_BATCH_ID_PATTERN = Pattern.compile("batchId=([^)\\s]+)");

    private final UserContextManager userContextManager;
    private final StorageGrpcClient storageGrpcClient;

    @Tool("Đổi tên của tệp tin hoặc thư mục sang tên mới")
    @SafeAction(actionType = "RENAME", supportUndo = true)
    public String renameNode(
            @P("ID của tệp tin hoặc thư mục cần đổi tên") String nodeId,
            @P("Tên mới cho tệp tin hoặc thư mục") String newName
    ) {
        UUID userId = userContextManager.getCurrentUserId();
        log.info("Executing safe rename via gRPC: user={}, node={}, newName={}", userId, nodeId, newName);

        NodeItemResponse updated = storageGrpcClient.renameNode(userId, nodeId, newName);
        return String.format("Thành công: Đã đổi tên node %s thành '%s'", updated.getId(), updated.getName());
    }

    @Tool
    @SafeAction(actionType = "CREATE_FOLDER", supportUndo = true)
    public String createFolder(
            String folderName,
            String parentId
    ) {
        UUID userId = userContextManager.getCurrentUserId();
        log.info("Executing safe folder creation via gRPC: user={}, name={}, parent={}", userId, folderName, parentId);

        NodeItemResponse created = storageGrpcClient.createFolder(userId, parentId, folderName);
        ChatTurnContext turnContext = userContextManager.getCurrentTurnContext();
        if (turnContext != null) {
            String undoTargetId = !created.getFirstCreatedNodeId().isBlank()
                    ? created.getFirstCreatedNodeId()
                    : created.getId();
            turnContext.setLastCreatedResourceId(undoTargetId);
        }
        return String.format("Thành công: Đã tạo thư mục '%s' với ID: %s", created.getName(), created.getId());
    }

    @SafeAction(actionType = "MOVE", supportUndo = true)
    public String moveNode(
            String nodeId,
            String targetFolderId
    ) {
        UUID userId = userContextManager.getCurrentUserId();
        log.info("Executing safe move via gRPC: user={}, node={}, targetFolder={}", userId, nodeId, targetFolderId);

        NodeItemResponse moved = storageGrpcClient.moveNode(userId, nodeId, targetFolderId);
        String destId = !moved.getParentId().isBlank() ? moved.getParentId() : targetFolderId;
        return String.format("Thành công: Đã di chuyển node %s ('%s') vào thư mục %s", moved.getId(), moved.getName(), destId);
    }

    @Tool
    @SafeAction(actionType = "SOFT_DELETE", supportUndo = true)
    public String moveToTrash(
            String nodeId
    ) {
        UUID userId = userContextManager.getCurrentUserId();
        log.info("Executing safe soft-delete via gRPC: user={}, node={}", userId, nodeId);

        SoftDeleteNodeGrpcResponse deleted = storageGrpcClient.softDeleteNode(userId, nodeId);
        ChatTurnContext turnContext = userContextManager.getCurrentTurnContext();
        if (turnContext != null) {
            turnContext.setLastDeleteBatchId(deleted.getDeleteBatchId());
        }
        return String.format(
                "Thành công: Đã đưa node %s ('%s') vào thùng rác (batchId=%s). Người dùng có thể khôi phục bất cứ lúc nào.",
                deleted.getNodeId(),
                deleted.getName(),
                deleted.getDeleteBatchId()
        );
    }

    @Tool
    @SafeAction(actionType = "STAR", supportUndo = true)
    public String toggleStar(
            String nodeId,
            boolean starred
    ) {
        UUID userId = userContextManager.getCurrentUserId();
        log.info("Executing safe star toggle via gRPC: user={}, node={}, starred={}", userId, nodeId, starred);

        NodeItemResponse updated = storageGrpcClient.toggleStarNode(userId, nodeId, starred);
        return String.format(
                "Thành công: Đã %s sao cho node %s ('%s')",
                updated.getIsStarred() ? "gắn" : "bỏ",
                updated.getId(),
                updated.getName()
        );
    }

    // =========================================================================
    // Undoable implementation support
    // =========================================================================

    @Override
    public Set<String> supportedActionTypes() {
        return UNDOABLE_ACTIONS;
    }

    @Override
    public UndoSnapshotPayload capturePreState(
            UUID userId,
            String actionType,
            String targetNodeId,
            Object[] rawArgs
    ) {
        // Tool tạo thư mục
        if ("CREATE_FOLDER".equals(actionType)) {
            String folderName = (rawArgs != null && rawArgs.length > 0 && rawArgs[0] != null)
                    ? rawArgs[0].toString().trim()
                    : null;
            String parentId = (rawArgs != null && rawArgs.length > 1 && rawArgs[1] != null && !rawArgs[1].toString().isBlank())
                    ? rawArgs[1].toString().trim()
                    : "root";

            return NodeMutationUndoSnapshot.builder()
                    .category(UndoSnapshotPayload.SnapshotCategory.NODE)
                    .actionType("CREATE_FOLDER")
                    .inverseActionType(InverseActionType.SOFT_DELETE_CREATED_NODE)
                    .isFolder(true)
                    .newName(folderName)
                    .newParentId(parentId)
                    .build();
        }

        NodeItemResponse node = storageGrpcClient.getNodeMetadata(userId, targetNodeId);
        if (node == null) {
            return null;
        }

        InverseActionType inverseAction = switch (actionType) {
            case "RENAME" -> InverseActionType.RENAME_NODE;
            case "MOVE" -> InverseActionType.MOVE_NODE;
            case "STAR" -> InverseActionType.TOGGLE_STAR_NODE;
            case "SOFT_DELETE" -> InverseActionType.RESTORE_FROM_TRASH;
            default -> InverseActionType.NONE;
        };

        NodeMutationUndoSnapshot.NodeMutationUndoSnapshotBuilder<?, ?> builder = NodeMutationUndoSnapshot.builder()
                .category(UndoSnapshotPayload.SnapshotCategory.NODE)
                .actionType(actionType)
                .inverseActionType(inverseAction)
                .nodeId(node.getId())
                .isFolder(node.getIsFolder())
                .previousName(node.getName())
                .previousParentId(node.getParentId())
                .previousStarred(node.getIsStarred())
                .previousStatus(node.getNodeStatus());

        if ("RENAME".equals(actionType) && rawArgs != null && rawArgs.length > 1 && rawArgs[1] != null) {
            builder.newName(rawArgs[1].toString().trim());
        } else if ("MOVE".equals(actionType) && rawArgs != null && rawArgs.length > 1 && rawArgs[1] != null) {
            builder.newParentId(rawArgs[1].toString().trim());
        } else if ("STAR".equals(actionType) && rawArgs != null && rawArgs.length > 1 && rawArgs[1] instanceof Boolean b) {
            builder.newStarred(b);
        } else if ("SOFT_DELETE".equals(actionType)) {
            builder.newStatus("DELETED");
        }
        return builder.build();
    }

    @Override
    public UndoSnapshotPayload capturePostState(
            UUID userId,
            String actionType,
            UndoSnapshotPayload preSnapshot,
            Object toolResult,
            ChatTurnContext turnContext
    ) {
        if (!(preSnapshot instanceof NodeMutationUndoSnapshot nodeSnap)) {
            return preSnapshot;
        }

        // Tool tạo thư mục
        if ("CREATE_FOLDER".equals(actionType)) {
            String createdId = turnContext != null ? turnContext.getLastCreatedResourceId() : null;
            if ((createdId == null || createdId.isBlank()) && toolResult != null) {
                Matcher matcher = CREATED_NODE_ID_PATTERN.matcher(toolResult.toString());
                if (matcher.find()) {
                    createdId = matcher.group(1).trim();
                }
            }
            if (createdId != null && !createdId.isBlank()) {
                nodeSnap.setCreatedNodeId(createdId);
                nodeSnap.setNodeId(createdId);
            } else {
                nodeSnap.setSupportUndo(false);
                nodeSnap.setInverseActionType(InverseActionType.NONE);
            }
        } else if ("SOFT_DELETE".equals(actionType)) {
            String batchId = turnContext != null ? turnContext.getLastDeleteBatchId() : null;
            if ((batchId == null || batchId.isBlank()) && toolResult != null) {
                Matcher matcher = DELETE_BATCH_ID_PATTERN.matcher(toolResult.toString());
                if (matcher.find()) {
                    batchId = matcher.group(1).trim();
                }
            }
            if (batchId != null && !batchId.isBlank()) {
                nodeSnap.setDeleteBatchId(batchId);
            }
        }
        return nodeSnap;
    }

    @Override
    public String revert(UUID userId, UndoSnapshotPayload snapshot) {
        if (!(snapshot instanceof NodeMutationUndoSnapshot snap)) {
            throw new BadRequestException("Snapshot payload không hợp lệ cho StorageSafeTools");
        }
        InverseActionType inverseAction = snap.getInverseActionType();
        if (inverseAction == null || inverseAction == InverseActionType.NONE) {
            throw new BadRequestException("Hành động không hỗ trợ hoàn tác");
        }

        return switch (inverseAction) {
            // Khôi phục lại hành động đổi tên
            case RENAME_NODE -> {
                requireNonBlank(snap.getNodeId(), "nodeId");
                requireNonBlank(snap.getPreviousName(), "previousName");
                storageGrpcClient.renameNode(userId, snap.getNodeId(), snap.getPreviousName());
                yield String.format("Đã khôi phục tên node %s về '%s'", snap.getNodeId(), snap.getPreviousName());
            }
            // Khôi phục lại hành động di chuyển node
            case MOVE_NODE -> {
                requireNonBlank(snap.getNodeId(), "nodeId");
                String targetParent = (snap.getPreviousParentId() != null && !snap.getPreviousParentId().isBlank())
                        ? snap.getPreviousParentId()
                        : "root";
                storageGrpcClient.moveNode(userId, snap.getNodeId(), targetParent);
                yield String.format("Đã di chuyển node %s về thư mục cũ (%s)", snap.getNodeId(), targetParent);
            }
            // Khôi phục lại hành động đánh dấu sao cho node
            case TOGGLE_STAR_NODE -> {
                requireNonBlank(snap.getNodeId(), "nodeId");
                boolean prevStarred = Boolean.TRUE.equals(snap.getPreviousStarred());
                storageGrpcClient.toggleStarNode(userId, snap.getNodeId(), prevStarred);
                yield String.format("Đã khôi phục trạng thái gắn sao (%s) cho node %s", prevStarred, snap.getNodeId());
            }
            // Khôi phục lại hành động khôi phục node
            case RESTORE_FROM_TRASH -> {
                requireNonBlank(snap.getNodeId(), "nodeId");
                storageGrpcClient.restoreNode(userId, snap.getNodeId());
                yield String.format("Đã khôi phục node %s từ thùng rác", snap.getNodeId());
            }
            case SOFT_DELETE_CREATED_NODE -> {
                String createdId = (snap.getCreatedNodeId() != null && !snap.getCreatedNodeId().isBlank())
                        ? snap.getCreatedNodeId()
                        : snap.getNodeId();
                requireNonBlank(createdId, "createdNodeId");
                storageGrpcClient.softDeleteNode(userId, createdId);
                yield String.format("Đã chuyển thư mục mới tạo (%s) vào thùng rác", createdId);
            }
            default -> throw new BadRequestException("Hanh động không được hỗ trợ " + inverseAction);
        };
    }

    private void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Thiếu trường '" + field + "' trong undo snapshot để hoàn tác");
        }
    }
}
