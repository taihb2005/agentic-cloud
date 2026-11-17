package vn.edu.hust.kat.storageservice.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.common.api.exception.DuplicateResourceException;
import vn.edu.hust.kat.common.api.exception.ResourceNotFoundException;
import vn.edu.hust.kat.common.api.response.BatchOperationResponse;
import vn.edu.hust.kat.common.api.response.FailureCode;
import vn.edu.hust.kat.common.api.response.OperationFailure;
import vn.edu.hust.kat.proto.storage.v1.*;
import vn.edu.hust.kat.storageservice.dto.request.ConfigureShareRequest;
import vn.edu.hust.kat.storageservice.dto.request.MoveNodeRequest;
import vn.edu.hust.kat.storageservice.dto.request.UpdateNodeRequest;
import vn.edu.hust.kat.storageservice.dto.response.*;
import vn.edu.hust.kat.storageservice.entity.FsNode;
import vn.edu.hust.kat.storageservice.repository.FsNodeRepository;
import vn.edu.hust.kat.storageservice.service.*;
import vn.edu.hust.kat.storageservice.utils.Constants;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserStorageGrpcService extends StorageServiceGrpc.StorageServiceImplBase {

    private final StorageUserService storageUserService;
    private final QuotaService quotaService;
    private final FsNodeService fsNodeService;
    private final FsNodeFolderService fsNodeFolderService;
    private final FsNodeTrashService fsNodeTrashService;
    private final FsNodeShareService fsNodeShareService;
    private final MinioStorageService minioStorageService;
    private final FsNodeRepository fsNodeRepository;

    @Override
    public void getUserStorage(
            GetUserStorageRequest request,
            StreamObserver<GetUserStorageResponse> responseObserver
    ) {
        try {
            UUID userId = parseUserId(request.getUserId());
            UserStorageResponse uResponse = storageUserService.getUserStorage(userId);

            GetUserStorageResponse getResponse = GetUserStorageResponse.newBuilder()
                    .setUserId(uResponse.getUserId().toString())
                    .setStoragePrefix(nullSafe(uResponse.getStoragePrefix()))
                    .setStorageBucketName(nullSafe(uResponse.getStorageBucketName()))
                    .setStorageMaxQuotaBytes(uResponse.getStorageMaxQuotaBytes() != null ? uResponse.getStorageMaxQuotaBytes() : 0L)
                    .setStorageCurrentQuotaBytes(uResponse.getStorageCurrentQuotaBytes() != null ? uResponse.getStorageCurrentQuotaBytes() : 0L)
                    .setRootNodeId(uResponse.getRootNodeId() != null ? uResponse.getRootNodeId() : 0L)
                    .setRootNodeName(nullSafe(uResponse.getRootNodeName()))
                    .setRootNodePath(nullSafe(uResponse.getRootNodePath()))
                    .setCreatedAt(uResponse.getCreatedAt() != null ? uResponse.getCreatedAt().toString() : "")
                    .setUpdatedAt(uResponse.getUpdatedAt() != null ? uResponse.getUpdatedAt().toString() : "")
                    .build();

            responseObserver.onNext(getResponse);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(e, responseObserver);
        }
    }

    @Override
    public void getUserQuota(
            GetUserQuotaRequest request,
            StreamObserver<GetUserQuotaResponse> responseObserver
    ) {
        try {
            UUID userId = parseUserId(request.getUserId());
            UserQuotaResponse quota = quotaService.getUserQuota(userId);

            GetUserQuotaResponse response = GetUserQuotaResponse.newBuilder()
                    .setUserId(quota.getUserId().toString())
                    .setCurrentQuotaBytes(quota.getCurrentQuota() != null ? quota.getCurrentQuota() : 0L)
                    .setMaxQuotaBytes(quota.getMaxQuota() != null ? quota.getMaxQuota() : 0L)
                    .setUsagePercentage(quota.getUsagePercentage() != null ? quota.getUsagePercentage() : 0.0)
                    .setStatus(nullSafe(quota.getStatus()))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(e, responseObserver);
        }
    }

    @Override
    public void searchNodes(
            SearchNodesRequest request,
            StreamObserver<SearchNodesResponse> responseObserver
    ) {
        try {
            UUID userId = parseUserId(request.getUserId());
            int page = Math.max(0, request.getPage());
            int size = request.getSize() > 0 ? request.getSize() : 20;
            List<String> sorts = request.getSortsList().isEmpty()
                    ? List.of("name,asc")
                    : request.getSortsList();

            PagedResponse<NodeResponse> pageResult = fsNodeService.getChildNodes(
                    userId,
                    request.getParentId(),
                    request.getNodeType(),
                    request.getName(),
                    request.getMimeType(),
                    page,
                    size,
                    sorts
            );

            SearchNodesResponse.Builder builder = SearchNodesResponse.newBuilder()
                    .setTotalElements(pageResult.getTotalElements());

            if (pageResult.getContent() != null) {
                for (NodeResponse item : pageResult.getContent()) {
                    builder.addNodes(mapToNodeItem(item));
                }
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(e, responseObserver);
        }
    }

    @Override
    public void getNodeMetadata(
            GetNodeMetadataRequest request,
            StreamObserver<NodeItemResponse> responseObserver
    ) {
        try {
            UUID userId = parseUserId(request.getUserId());
            NodeResponse metadata = fsNodeService.getNodeMetadata(userId, request.getNodeId());
            responseObserver.onNext(mapToNodeItem(metadata));
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(e, responseObserver);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void readFileContent(
            ReadFileContentRequest request,
            StreamObserver<ReadFileContentResponse> responseObserver
    ) {
        try {
            UUID userId = parseUserId(request.getUserId());
            FsNode node = fsNodeService.resolveAndValidateNode(userId, request.getNodeId(), true);

            if (Boolean.TRUE.equals(node.getIsFolder())) {
                throw new BadRequestException("Node '" + node.getName() + "' là thư mục, không thể đọc nội dung văn bản");
            }

            String content = minioStorageService.readObjectContent(
                    node.getStorageBucketName(),
                    node.getS3ObjectKey()
            );

            ReadFileContentResponse response = ReadFileContentResponse.newBuilder()
                    .setNodeId(String.valueOf(node.getId()))
                    .setName(nullSafe(node.getName()))
                    .setMimeType(nullSafe(node.getMimeType()))
                    .setSizeBytes(node.getSizeBytes() != null ? node.getSizeBytes() : 0L)
                    .setContent(nullSafe(content))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(e, responseObserver);
        }
    }

    @Override
    public void renameNode(
            RenameNodeRequest request,
            StreamObserver<NodeItemResponse> responseObserver
    ) {
        try {
            UUID userId = parseUserId(request.getUserId());
            if (request.getNewName() == null || request.getNewName().isBlank()) {
                throw new BadRequestException("Tên mới không được để trống");
            }

            UpdateNodeRequest updateRequest = UpdateNodeRequest.builder()
                    .name(request.getNewName().trim())
                    .build();
            NodeResponse updated = fsNodeService.updateNode(userId, request.getNodeId(), updateRequest);

            responseObserver.onNext(mapToNodeItem(updated));
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(e, responseObserver);
        }
    }

    @Override
    public void createFolder(
            CreateFolderGrpcRequest request,
            StreamObserver<NodeItemResponse> responseObserver
    ) {
        try {
            UUID userId = parseUserId(request.getUserId());
            NodeResponse created = fsNodeFolderService.createFolder(
                    userId,
                    request.getParentId(),
                    request.getFolderName()
            );

            responseObserver.onNext(mapToNodeItem(created));
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(e, responseObserver);
        }
    }

    @Override
    public void moveNode(
            MoveNodeGrpcRequest request,
            StreamObserver<NodeItemResponse> responseObserver
    ) {
        try {
            UUID userId = parseUserId(request.getUserId());
            String dstNodeId = (request.getTargetFolderId() != null && !request.getTargetFolderId().isBlank())
                    ? request.getTargetFolderId().trim()
                    : null;

            MoveNodeRequest moveRequest = MoveNodeRequest.builder()
                    .srcNodeIds(List.of(request.getNodeId()))
                    .dstNodeId(dstNodeId)
                    .build();

            BatchOperationResponse<MoveResult, NodeFailureResult> batchResult =
                    fsNodeService.moveNodes(userId, moveRequest);

            if (batchResult.getFailedCount() > 0 && !batchResult.getFailed().isEmpty()) {
                throwFromOperationFailure(batchResult.getFailed().get(0));
            }

            NodeResponse updated = fsNodeService.getNodeMetadata(userId, request.getNodeId());
            responseObserver.onNext(mapToNodeItem(updated));
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(e, responseObserver);
        }
    }

    @Override
    public void softDeleteNode(
            SoftDeleteNodeGrpcRequest request,
            StreamObserver<SoftDeleteNodeGrpcResponse> responseObserver
    ) {
        try {
            UUID userId = parseUserId(request.getUserId());
            NodeResponse existing = fsNodeService.getNodeMetadata(userId, request.getNodeId());

            BatchOperationResponse<DeleteResult, NodeFailureResult> batchResult =
                    fsNodeService.softDeleteNodes(userId, List.of(request.getNodeId()));

            if (batchResult.getFailedCount() > 0 && !batchResult.getFailed().isEmpty()) {
                throwFromOperationFailure(batchResult.getFailed().get(0));
            }

            String deleteBatchId = (!batchResult.getSucceeded().isEmpty() && batchResult.getSucceeded().get(0).getOperationId() != null)
                    ? batchResult.getSucceeded().get(0).getOperationId()
                    : "";

            SoftDeleteNodeGrpcResponse response = SoftDeleteNodeGrpcResponse.newBuilder()
                    .setNodeId(String.valueOf(existing.getId()))
                    .setName(nullSafe(existing.getName()))
                    .setDeleteBatchId(deleteBatchId)
                    .setMessage("Đã đưa '" + existing.getName() + "' vào thùng rác")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(e, responseObserver);
        }
    }

    @Override
    public void toggleStarNode(
            ToggleStarNodeRequest request,
            StreamObserver<NodeItemResponse> responseObserver
    ) {
        try {
            UUID userId = parseUserId(request.getUserId());
            UpdateNodeRequest updateRequest = UpdateNodeRequest.builder()
                    .starred(request.getStarred())
                    .build();
            NodeResponse updated = fsNodeService.updateNode(userId, request.getNodeId(), updateRequest);

            responseObserver.onNext(mapToNodeItem(updated));
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(e, responseObserver);
        }
    }

    @Override
    @Transactional
    public void permanentDeleteNode(
            PermanentDeleteNodeRequest request,
            StreamObserver<PermanentDeleteNodeResponse> responseObserver
    ) {
        try {
            UUID userId = parseUserId(request.getUserId());
            Long nodeId = fsNodeService.parseNodeId(request.getNodeId());

            FsNode node = fsNodeRepository.findById(nodeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Node không tồn tại"));

            if (node.getUser() == null || !userId.equals(node.getUser().getId())) {
                throw new ResourceNotFoundException("Node không tồn tại");
            }

            if (node.getParent() == null || Constants.STORAGE_ROOT_NAME.equalsIgnoreCase(node.getName())) {
                throw new BadRequestException("Không thể xóa vĩnh viễn thư mục gốc");
            }

            String nodeName = node.getName();

            if (!fsNodeService.isNodeDeleted(node)) {
                fsNodeService.softDeleteNode(userId, String.valueOf(nodeId));
            }

            BatchOperationResponse<DeleteResult, NodeFailureResult> batchResult =
                    fsNodeTrashService.permanentDeleteTrashSync(userId, List.of(String.valueOf(nodeId)));

            if (batchResult.getFailedCount() > 0 && !batchResult.getFailed().isEmpty()) {
                throwFromOperationFailure(batchResult.getFailed().get(0));
            }

            PermanentDeleteNodeResponse response = PermanentDeleteNodeResponse.newBuilder()
                    .setNodeId(String.valueOf(nodeId))
                    .setName(nullSafe(nodeName))
                    .setDeletedCount(batchResult.getSuccessCount())
                    .setMessage("Đã xóa vĩnh viễn '" + nodeName + "'")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(e, responseObserver);
        }
    }

    @Override
    public void createPublicShare(
            CreatePublicShareRequest request,
            StreamObserver<CreatePublicShareResponse> responseObserver
    ) {
        try {
            UUID userId = parseUserId(request.getUserId());
            Long nodeId = fsNodeService.parseNodeId(request.getNodeId());
            String permission = (request.getPermission() != null && !request.getPermission().isBlank())
                    ? request.getPermission().trim().toUpperCase()
                    : "VIEW";

            ConfigureShareRequest shareRequest = ConfigureShareRequest.builder()
                    .shareMode(FsNodeShareService.MODE_PUBLIC_LINK)
                    .permission(permission)
                    .overrideExisting(true)
                    .build();

            NodeShareDetailResponse detail = fsNodeShareService.configureShare(userId, nodeId, shareRequest);

            CreatePublicShareResponse response = CreatePublicShareResponse.newBuilder()
                    .setNodeId(String.valueOf(detail.getNodeId()))
                    .setNodeName(nullSafe(detail.getNodeName()))
                    .setIsFolder(Boolean.TRUE.equals(detail.getIsFolder()))
                    .setShareMode(nullSafe(detail.getShareMode()))
                    .setPermission(nullSafe(detail.getPermission()))
                    .setSharedUrl(nullSafe(detail.getSharedUrl()))
                    .setShareToken(nullSafe(detail.getShareToken()))
                    .setStatus(nullSafe(detail.getStatus()))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(e, responseObserver);
        }
    }

    private UUID parseUserId(String rawUserId) {
        if (rawUserId == null || rawUserId.isBlank()) {
            throw new BadRequestException("User ID không hợp lệ");
        }
        try {
            return UUID.fromString(rawUserId.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("User ID không đúng định dạng UUID");
        }
    }

    private NodeItemResponse mapToNodeItem(NodeResponse node) {
        return NodeItemResponse.newBuilder()
                .setId(node.getId() != null ? String.valueOf(node.getId()) : "")
                .setParentId(node.getParentId() != null ? String.valueOf(node.getParentId()) : "")
                .setName(nullSafe(node.getName()))
                .setIsFolder(Boolean.TRUE.equals(node.getIsFolder()))
                .setNodeStatus(nullSafe(node.getNodeStatus()))
                .setMimeType(nullSafe(node.getMimeType()))
                .setSizeBytes(node.getSizeBytes() != null ? node.getSizeBytes() : 0L)
                .setIsStarred(Boolean.TRUE.equals(node.getIsStarred()))
                .setCreatedAt(node.getCreatedAt() != null ? node.getCreatedAt().toString() : "")
                .setUpdatedAt(node.getUpdatedAt() != null ? node.getUpdatedAt().toString() : "")
                .build();
    }

    private void throwFromOperationFailure(OperationFailure<NodeFailureResult> failure) {
        String message = failure.getMessage() != null ? failure.getMessage() : "Thao tác thất bại";
        String code = failure.getCode();
        if (FailureCode.NODE_NOT_FOUND.getCode().equals(code)
                || FailureCode.DESTINATION_NOT_FOUND.getCode().equals(code)
                || FailureCode.NOT_FOUND.getCode().equals(code)) {
            throw new ResourceNotFoundException(message);
        }
        if (FailureCode.NAME_ALREADY_EXISTS.getCode().equals(code)
                || FailureCode.ALREADY_EXISTS.getCode().equals(code)) {
            throw new DuplicateResourceException(message);
        }
        throw new BadRequestException(message);
    }

    private void handleError(Exception e, StreamObserver<?> responseObserver) {
        log.warn("gRPC StorageService error: {}", e.getMessage());
        if (e instanceof ResourceNotFoundException) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        } else if (e instanceof DuplicateResourceException) {
            responseObserver.onError(Status.ALREADY_EXISTS.withDescription(e.getMessage()).asRuntimeException());
        } else if (e instanceof BadRequestException) {
            responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException());
        } else if (e instanceof AccessDeniedException) {
            responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).asRuntimeException());
        } else if (e instanceof IllegalArgumentException) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } else {
            log.error("Unhandled gRPC error in UserStorageGrpcService", e);
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription(e.getMessage() != null ? e.getMessage() : "Internal storage error")
                            .asRuntimeException()
            );
        }
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
