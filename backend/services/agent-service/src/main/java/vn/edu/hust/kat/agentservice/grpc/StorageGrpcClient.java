package vn.edu.hust.kat.agentservice.grpc;

import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.hust.kat.agentservice.util.GrpcStatusRuntimeMapper;
import vn.edu.hust.kat.proto.storage.v1.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class StorageGrpcClient {

    private static final long DEFAULT_DEADLINE_SECONDS = 5L;

    private final StorageServiceGrpc.StorageServiceBlockingStub storageStub;

    public GetUserStorageResponse getUserStorage(UUID userId) {
        GetUserStorageRequest request = GetUserStorageRequest.newBuilder()
                .setUserId(userId.toString())
                .build();
        try {
            return storageStub
                    .withDeadlineAfter(DEFAULT_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .getUserStorage(request);
        } catch (StatusRuntimeException ex) {
            log.error("gRPC getUserStorage failed for user {}: {}", userId, ex.getMessage());
            throw GrpcStatusRuntimeMapper.throwFrom(ex);
        }
    }

    public GetUserQuotaResponse getUserQuota(UUID userId) {
        GetUserQuotaRequest request = GetUserQuotaRequest.newBuilder()
                .setUserId(userId.toString())
                .build();
        try {
            return storageStub
                    .withDeadlineAfter(DEFAULT_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .getUserQuota(request);
        } catch (StatusRuntimeException ex) {
            log.error("gRPC getUserQuota failed for user {}: {}", userId, ex.getMessage());
            throw GrpcStatusRuntimeMapper.throwFrom(ex);
        }
    }

    public SearchNodesResponse searchNodes(
            UUID userId,
            String parentId,
            String nodeType,
            String name,
            String mimeType,
            int page,
            int size,
            List<String> sorts
    ) {
        SearchNodesRequest.Builder builder = SearchNodesRequest.newBuilder()
                .setUserId(userId.toString())
                .setParentId(parentId != null ? parentId.trim() : "")
                .setNodeType(nodeType != null ? nodeType.trim() : "")
                .setName(name != null ? name.trim() : "")
                .setMimeType(mimeType != null ? mimeType.trim() : "")
                .setPage(Math.max(0, page))
                .setSize(size > 0 ? size : 20);

        if (sorts != null && !sorts.isEmpty()) {
            builder.addAllSorts(sorts);
        } else {
            builder.addSorts("name,asc");
        }

        try {
            return storageStub
                    .withDeadlineAfter(DEFAULT_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .searchNodes(builder.build());
        } catch (StatusRuntimeException ex) {
            log.error("gRPC searchNodes failed for user={}, parentId={}, name={}: {}", userId, parentId, name, ex.getMessage());
            throw GrpcStatusRuntimeMapper.throwFrom(ex);
        }
    }

    public NodeItemResponse getNodeMetadata(UUID userId, String nodeId) {
        GetNodeMetadataRequest request = GetNodeMetadataRequest.newBuilder()
                .setUserId(userId.toString())
                .setNodeId(nodeId != null ? nodeId.trim() : "")
                .build();
        try {
            return storageStub
                    .withDeadlineAfter(DEFAULT_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .getNodeMetadata(request);
        } catch (StatusRuntimeException ex) {
            log.error("gRPC getNodeMetadata failed for user={}, node={}: {}", userId, nodeId, ex.getMessage());
            throw GrpcStatusRuntimeMapper.throwFrom(ex);
        }
    }

    public ReadFileContentResponse readFileContent(UUID userId, String nodeId) {
        ReadFileContentRequest request = ReadFileContentRequest.newBuilder()
                .setUserId(userId.toString())
                .setNodeId(nodeId != null ? nodeId.trim() : "")
                .build();
        try {
            return storageStub
                    .withDeadlineAfter(DEFAULT_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .readFileContent(request);
        } catch (StatusRuntimeException ex) {
            log.error("gRPC readFileContent failed for user={}, node={}: {}", userId, nodeId, ex.getMessage());
            throw GrpcStatusRuntimeMapper.throwFrom(ex);
        }
    }

    public NodeItemResponse renameNode(UUID userId, String nodeId, String newName) {
        RenameNodeRequest request = RenameNodeRequest.newBuilder()
                .setUserId(userId.toString())
                .setNodeId(nodeId != null ? nodeId.trim() : "")
                .setNewName(newName != null ? newName.trim() : "")
                .build();
        try {
            return storageStub
                    .withDeadlineAfter(DEFAULT_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .renameNode(request);
        } catch (StatusRuntimeException ex) {
            log.error("gRPC renameNode failed for user={}, node={}, newName={}: {}", userId, nodeId, newName, ex.getMessage());
            throw GrpcStatusRuntimeMapper.throwFrom(ex);
        }
    }

    public NodeItemResponse createFolder(UUID userId, String parentId, String folderName) {
        CreateFolderGrpcRequest request = CreateFolderGrpcRequest.newBuilder()
                .setUserId(userId.toString())
                .setParentId(parentId != null ? parentId.trim() : "")
                .setFolderName(folderName != null ? folderName.trim() : "")
                .build();
        try {
            return storageStub
                    .withDeadlineAfter(DEFAULT_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .createFolder(request);
        } catch (StatusRuntimeException ex) {
            log.error("gRPC createFolder failed for user={}, parentId={}, folderName={}: {}", userId, parentId, folderName, ex.getMessage());
            throw GrpcStatusRuntimeMapper.throwFrom(ex);
        }
    }

    public NodeItemResponse moveNode(UUID userId, String nodeId, String targetFolderId) {
        MoveNodeGrpcRequest request = MoveNodeGrpcRequest.newBuilder()
                .setUserId(userId.toString())
                .setNodeId(nodeId != null ? nodeId.trim() : "")
                .setTargetFolderId(targetFolderId != null ? targetFolderId.trim() : "")
                .build();
        try {
            return storageStub
                    .withDeadlineAfter(DEFAULT_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .moveNode(request);
        } catch (StatusRuntimeException ex) {
            log.error("gRPC moveNode failed for user={}, node={}, targetFolder={}: {}", userId, nodeId, targetFolderId, ex.getMessage());
            throw GrpcStatusRuntimeMapper.throwFrom(ex);
        }
    }

    public SoftDeleteNodeGrpcResponse softDeleteNode(UUID userId, String nodeId) {
        SoftDeleteNodeGrpcRequest request = SoftDeleteNodeGrpcRequest.newBuilder()
                .setUserId(userId.toString())
                .setNodeId(nodeId != null ? nodeId.trim() : "")
                .build();
        try {
            return storageStub
                    .withDeadlineAfter(DEFAULT_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .softDeleteNode(request);
        } catch (StatusRuntimeException ex) {
            log.error("gRPC softDeleteNode failed for user={}, node={}: {}", userId, nodeId, ex.getMessage());
            throw GrpcStatusRuntimeMapper.throwFrom(ex);
        }
    }

    public NodeItemResponse toggleStarNode(UUID userId, String nodeId, boolean starred) {
        ToggleStarNodeRequest request = ToggleStarNodeRequest.newBuilder()
                .setUserId(userId.toString())
                .setNodeId(nodeId != null ? nodeId.trim() : "")
                .setStarred(starred)
                .build();
        try {
            return storageStub
                    .withDeadlineAfter(DEFAULT_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .toggleStarNode(request);
        } catch (StatusRuntimeException ex) {
            log.error("gRPC toggleStarNode failed for user={}, node={}, starred={}: {}", userId, nodeId, starred, ex.getMessage());
            throw GrpcStatusRuntimeMapper.throwFrom(ex);
        }
    }

    public PermanentDeleteNodeResponse permanentDeleteNode(UUID userId, String nodeId) {
        PermanentDeleteNodeRequest request = PermanentDeleteNodeRequest.newBuilder()
                .setUserId(userId.toString())
                .setNodeId(nodeId != null ? nodeId.trim() : "")
                .build();
        try {
            return storageStub
                    .withDeadlineAfter(DEFAULT_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .permanentDeleteNode(request);
        } catch (StatusRuntimeException ex) {
            log.error("gRPC permanentDeleteNode failed for user={}, node={}: {}", userId, nodeId, ex.getMessage());
            throw GrpcStatusRuntimeMapper.throwFrom(ex);
        }
    }

    public CreatePublicShareResponse createPublicShare(UUID userId, String nodeId, String permission) {
        CreatePublicShareRequest request = CreatePublicShareRequest.newBuilder()
                .setUserId(userId.toString())
                .setNodeId(nodeId != null ? nodeId.trim() : "")
                .setPermission(permission != null && !permission.isBlank() ? permission.trim().toUpperCase() : "VIEW")
                .build();
        try {
            return storageStub
                    .withDeadlineAfter(DEFAULT_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .createPublicShare(request);
        } catch (StatusRuntimeException ex) {
            log.error("gRPC createPublicShare failed for user={}, node={}: {}", userId, nodeId, ex.getMessage());
            throw GrpcStatusRuntimeMapper.throwFrom(ex);
        }
    }

    public RestoreNodeGrpcResponse restoreNode(UUID userId, String nodeId) {
        RestoreNodeGrpcRequest request = RestoreNodeGrpcRequest.newBuilder()
                .setUserId(userId.toString())
                .setNodeId(nodeId != null ? nodeId.trim() : "")
                .build();
        try {
            return storageStub
                    .withDeadlineAfter(DEFAULT_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .restoreNode(request);
        } catch (StatusRuntimeException ex) {
            log.error("gRPC restoreNode failed for user={}, node={}: {}", userId, nodeId, ex.getMessage());
            throw GrpcStatusRuntimeMapper.throwFrom(ex);
        }
    }

    public NodeShareDetailGrpcResponse getNodeShareDetail(UUID userId, String nodeId) {
        GetNodeShareDetailRequest request = GetNodeShareDetailRequest.newBuilder()
                .setUserId(userId.toString())
                .setNodeId(nodeId != null ? nodeId.trim() : "")
                .build();
        try {
            return storageStub
                    .withDeadlineAfter(DEFAULT_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .getNodeShareDetail(request);
        } catch (StatusRuntimeException ex) {
            log.error("gRPC getNodeShareDetail failed for user={}, node={}: {}", userId, nodeId, ex.getMessage());
            throw GrpcStatusRuntimeMapper.throwFrom(ex);
        }
    }

    public RevokeAllShareResponse revokeAllShare(UUID userId, String nodeId) {
        RevokeAllShareRequest request = RevokeAllShareRequest.newBuilder()
                .setUserId(userId.toString())
                .setNodeId(nodeId != null ? nodeId.trim() : "")
                .build();
        try {
            return storageStub
                    .withDeadlineAfter(DEFAULT_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .revokeAllShare(request);
        } catch (StatusRuntimeException ex) {
            log.error("gRPC revokeAllShare failed for user={}, node={}: {}", userId, nodeId, ex.getMessage());
            throw GrpcStatusRuntimeMapper.throwFrom(ex);
        }
    }

    public NodeShareDetailGrpcResponse restoreShareConfig(
            UUID userId,
            String nodeId,
            String shareMode,
            String permission,
            String expiresAt,
            Integer maxAccessCount,
            List<String> sharedUserIds
    ) {
        RestoreShareConfigRequest.Builder builder = RestoreShareConfigRequest.newBuilder()
                .setUserId(userId.toString())
                .setNodeId(nodeId != null ? nodeId.trim() : "")
                .setShareMode(shareMode != null ? shareMode.trim() : "")
                .setPermission(permission != null ? permission.trim() : "")
                .setExpiresAt(expiresAt != null ? expiresAt.trim() : "")
                .setMaxAccessCount(maxAccessCount != null ? maxAccessCount : 0);
        if (sharedUserIds != null && !sharedUserIds.isEmpty()) {
            builder.addAllSharedUserIds(sharedUserIds);
        }
        try {
            return storageStub
                    .withDeadlineAfter(DEFAULT_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .restoreShareConfig(builder.build());
        } catch (StatusRuntimeException ex) {
            log.error("gRPC restoreShareConfig failed for user={}, node={}: {}", userId, nodeId, ex.getMessage());
            throw GrpcStatusRuntimeMapper.throwFrom(ex);
        }
    }
}
