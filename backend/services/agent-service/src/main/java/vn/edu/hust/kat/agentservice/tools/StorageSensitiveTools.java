package vn.edu.hust.kat.agentservice.tools;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.hust.kat.agentservice.context.ChatTurnContext;
import vn.edu.hust.kat.agentservice.context.UserContextManager;
import vn.edu.hust.kat.agentservice.dto.ActionProposalDto;
import vn.edu.hust.kat.agentservice.grpc.StorageGrpcClient;
import vn.edu.hust.kat.agentservice.guardrail.annotation.SensitiveAction;
import vn.edu.hust.kat.agentservice.guardrail.snapshot.body.InverseActionType;
import vn.edu.hust.kat.agentservice.guardrail.snapshot.body.ShareConfigState;
import vn.edu.hust.kat.agentservice.guardrail.snapshot.body.SharedUserEntry;
import vn.edu.hust.kat.agentservice.guardrail.snapshot.payload.ShareMutationUndoSnapshot;
import vn.edu.hust.kat.agentservice.guardrail.snapshot.payload.UndoSnapshotPayload;
import vn.edu.hust.kat.agentservice.guardrail.undo.Undoable;
import vn.edu.hust.kat.agentservice.service.ActionConfirmationService;
import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.proto.storage.v1.CreatePublicShareResponse;
import vn.edu.hust.kat.proto.storage.v1.NodeShareDetailGrpcResponse;
import vn.edu.hust.kat.proto.storage.v1.PermanentDeleteNodeResponse;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Tool descriptions and parameter prompts are loaded externally from {@code classpath:prompts/tool-prompts.yml}
 * via {@link vn.edu.hust.kat.agentservice.config.ToolPromptRegistry}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StorageSensitiveTools implements ITools, Undoable {

    private static final Gson GSON = new Gson();
    private static final Set<String> UNDOABLE_ACTIONS = Set.of("PUBLIC_SHARE");

    private final UserContextManager userContextManager;
    private final StorageGrpcClient storageGrpcClient;
    private final ActionConfirmationService actionConfirmationService;

    @Tool
    public String createActionProposal(String actionType, String targetNodeId, String explanation) {
        UUID userId = userContextManager.getCurrentUserId();
        ActionProposalDto proposal = actionConfirmationService.createProposal(
                userId,
                actionType,
                targetNodeId,
                explanation
        );

        log.warn("Created sensitive action proposal (PENDING_CONFIRMATION): user={}, action={}, node={}, proposalId={}",
                userId, proposal.getActionType(), targetNodeId, proposal.getProposalId());

        return GSON.toJson(proposal);
    }

    @Tool
    @SensitiveAction(actionType = "HARD_DELETE")
    public String permanentDelete(String nodeId, String confirmationToken) {
        UUID userId = userContextManager.getCurrentUserId();
        log.info("Executing hard-delete via gRPC: user={}, node={}", userId, nodeId);

        PermanentDeleteNodeResponse response = storageGrpcClient.permanentDeleteNode(userId, nodeId);
        return String.format(
                "Thành công: Đã xóa vĩnh viễn node %s ('%s'), số mục đã xóa: %d",
                response.getNodeId(),
                response.getName(),
                response.getDeletedCount()
        );
    }

    @Tool
    @SensitiveAction(actionType = "PUBLIC_SHARE", supportUndo = true)
    public String createPublicShare(String nodeId, String confirmationToken) {
        UUID userId = userContextManager.getCurrentUserId();
        log.info("Executing public share via gRPC: user={}, node={}", userId, nodeId);

        CreatePublicShareResponse response = storageGrpcClient.createPublicShare(userId, nodeId, "VIEW");
        return String.format(
                "Thành công: Đã tạo public link cho node %s ('%s'): %s (shareToken=%s)",
                response.getNodeId(),
                response.getNodeName(),
                response.getSharedUrl(),
                response.getShareToken()
        );
    }

    // =========================================================================
    // Undoable implementation
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
        if (storageGrpcClient == null || targetNodeId == null || targetNodeId.isBlank()) {
            return null;
        }
        try {
            NodeShareDetailGrpcResponse detail = storageGrpcClient.getNodeShareDetail(userId, targetNodeId);
            ShareConfigState prevState = mapShareDetail(detail);
            boolean wasPrivate = prevState == null
                    || prevState.getShareMode() == null
                    || prevState.getShareMode().isBlank()
                    || "PRIVATE".equalsIgnoreCase(prevState.getShareMode())
                    || "NONE".equalsIgnoreCase(prevState.getShareMode());

            InverseActionType inverseAction = wasPrivate
                    ? InverseActionType.REVOKE_ALL_SHARE
                    : InverseActionType.RESTORE_SHARE_CONFIG;

            return ShareMutationUndoSnapshot.builder()
                    .category(UndoSnapshotPayload.SnapshotCategory.SHARE)
                    .actionType(actionType)
                    .inverseActionType(inverseAction)
                    .nodeId(targetNodeId)
                    .isFolder(detail != null ? detail.getIsFolder() : null)
                    .previousShareState(prevState)
                    .build();
        } catch (Exception e) {
            log.debug("Could not fetch pre-mutation share snapshot (node={}): {}", targetNodeId, e.getMessage());
            return ShareMutationUndoSnapshot.builder()
                    .category(UndoSnapshotPayload.SnapshotCategory.SHARE)
                    .actionType(actionType)
                    .inverseActionType(InverseActionType.REVOKE_ALL_SHARE)
                    .nodeId(targetNodeId)
                    .build();
        }
    }

    @Override
    public UndoSnapshotPayload capturePostState(
            UUID userId,
            String actionType,
            UndoSnapshotPayload preSnapshot,
            Object toolResult,
            ChatTurnContext turnContext
    ) {
        if (preSnapshot instanceof ShareMutationUndoSnapshot shareSnap
                && storageGrpcClient != null
                && shareSnap.getNodeId() != null
                && !shareSnap.getNodeId().isBlank()) {
            try {
                NodeShareDetailGrpcResponse detail = storageGrpcClient.getNodeShareDetail(userId, shareSnap.getNodeId());
                shareSnap.setNewShareState(mapShareDetail(detail));
            } catch (Exception e) {
                log.debug("Could not fetch post-mutation share snapshot (node={}): {}", shareSnap.getNodeId(), e.getMessage());
            }
        }
        return preSnapshot;
    }

    @Override
    public String revert(UUID userId, UndoSnapshotPayload snapshot) {
        if (!(snapshot instanceof ShareMutationUndoSnapshot snap)) {
            throw new BadRequestException("Snapshot payload không hợp lệ cho StorageSensitiveTools");
        }
        InverseActionType inverseAction = snap.getInverseActionType();
        if (inverseAction == null || inverseAction == InverseActionType.NONE) {
            throw new BadRequestException("Hành động chia sẻ này không hỗ trợ hoàn tác");
        }
        if (snap.getNodeId() == null || snap.getNodeId().isBlank()) {
            throw new BadRequestException("Thiếu nodeId trong share undo snapshot");
        }

        return switch (inverseAction) {
            case REVOKE_ALL_SHARE -> {
                storageGrpcClient.revokeAllShare(userId, snap.getNodeId());
                yield String.format("Đã thu hồi toàn bộ chia sẻ của node %s (trở về PRIVATE)", snap.getNodeId());
            }
            case RESTORE_SHARE_CONFIG, REMOVE_SHARED_USERS, RESTORE_REMOVED_SHARED_USERS, RESTORE_SHARE_PERMISSION -> {
                ShareConfigState prev = snap.getPreviousShareState();
                if (prev == null || prev.getShareMode() == null
                        || "PRIVATE".equalsIgnoreCase(prev.getShareMode())
                        || "NONE".equalsIgnoreCase(prev.getShareMode())) {
                    storageGrpcClient.revokeAllShare(userId, snap.getNodeId());
                    yield String.format("Đã thu hồi chia sẻ của node %s về trạng thái PRIVATE", snap.getNodeId());
                }

                List<String> sharedUserIds = prev.getSharedUsers() != null
                        ? prev.getSharedUsers().stream()
                        .map(SharedUserEntry::getUserId)
                        .filter(id -> id != null && !id.isBlank())
                        .toList()
                        : List.of();

                storageGrpcClient.restoreShareConfig(
                        userId,
                        snap.getNodeId(),
                        prev.getShareMode(),
                        prev.getPermission(),
                        prev.getExpiresAt(),
                        prev.getMaxAccessCount(),
                        sharedUserIds
                );
                yield String.format("Đã khôi phục cấu hình chia sẻ (%s) cho node %s", prev.getShareMode(), snap.getNodeId());
            }
            default -> throw new BadRequestException("StorageSensitiveTools không hỗ trợ inverseActionType=" + inverseAction);
        };
    }

    private ShareConfigState mapShareDetail(NodeShareDetailGrpcResponse detail) {
        if (detail == null) {
            return null;
        }
        List<SharedUserEntry> users = detail.getSharedUsersList().stream()
                .map(u -> SharedUserEntry.builder()
                        .userId(u.getUserId())
                        .username(u.getUsername())
                        .permission(u.getPermission())
                        .build())
                .toList();

        return ShareConfigState.builder()
                .shareMode(detail.getShareMode())
                .status(detail.getStatus())
                .permission(detail.getPermission().isBlank() ? null : detail.getPermission())
                .isPublic(detail.getIsPublic())
                .shareToken(detail.getShareToken().isBlank() ? null : detail.getShareToken())
                .sharedUrl(detail.getSharedUrl().isBlank() ? null : detail.getSharedUrl())
                .isPasswordProtected(detail.getIsPasswordProtected())
                .expiresAt(detail.getExpiresAt().isBlank() ? null : detail.getExpiresAt())
                .maxAccessCount(detail.getMaxAccessCount() > 0 ? detail.getMaxAccessCount() : null)
                .sharedUsers(users.isEmpty() ? null : users)
                .build();
    }
}
