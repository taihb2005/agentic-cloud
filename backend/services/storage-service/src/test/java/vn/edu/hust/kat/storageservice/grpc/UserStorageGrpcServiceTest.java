package vn.edu.hust.kat.storageservice.grpc;

import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.hust.kat.common.api.response.BatchOperationResponse;
import vn.edu.hust.kat.proto.storage.v1.*;
import vn.edu.hust.kat.storageservice.dto.response.*;
import vn.edu.hust.kat.storageservice.entity.FsNode;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.repository.FsNodeRepository;
import vn.edu.hust.kat.storageservice.service.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserStorageGrpcServiceTest {

    @Mock
    private StorageUserService storageUserService;

    @Mock
    private QuotaService quotaService;

    @Mock
    private FsNodeService fsNodeService;

    @Mock
    private FsNodeFolderService fsNodeFolderService;

    @Mock
    private FsNodeTrashService fsNodeTrashService;

    @Mock
    private FsNodeShareService fsNodeShareService;

    @Mock
    private MinioStorageService minioStorageService;

    @Mock
    private FsNodeRepository fsNodeRepository;

    @InjectMocks
    private UserStorageGrpcService grpcService;

    @Test
    @SuppressWarnings("unchecked")
    void searchNodes_ReturnsMatchingNodesForUser() {
        UUID userId = UUID.randomUUID();
        NodeResponse node = NodeResponse.builder()
                .id(12L)
                .parentId(1L)
                .name("invoice_2026.pdf")
                .isFolder(false)
                .nodeStatus("ACTIVE")
                .mimeType("application/pdf")
                .sizeBytes(4096L)
                .isStarred(false)
                .build();

        PagedResponse<NodeResponse> page = PagedResponse.<NodeResponse>builder()
                .content(List.of(node))
                .totalElements(1L)
                .build();

        when(fsNodeService.getChildNodes(userId, "", "FILE", "invoice", "application/pdf", 0, 20, List.of("name,asc")))
                .thenReturn(page);

        StreamObserver<SearchNodesResponse> observer = mock(StreamObserver.class);
        grpcService.searchNodes(
                SearchNodesRequest.newBuilder()
                        .setUserId(userId.toString())
                        .setParentId("")
                        .setNodeType("FILE")
                        .setName("invoice")
                        .setMimeType("application/pdf")
                        .setPage(0)
                        .setSize(20)
                        .addSorts("name,asc")
                        .build(),
                observer
        );

        ArgumentCaptor<SearchNodesResponse> captor = ArgumentCaptor.forClass(SearchNodesResponse.class);
        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();

        SearchNodesResponse response = captor.getValue();
        assertEquals(1, response.getNodesCount());
        assertEquals("invoice_2026.pdf", response.getNodes(0).getName());
        assertEquals("12", response.getNodes(0).getId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void readFileContent_ReadsFromMinioForUserFile() {
        UUID userId = UUID.randomUUID();
        FsUser user = FsUser.builder().id(userId).build();
        FsNode fileNode = FsNode.builder()
                .id(25L)
                .user(user)
                .name("notes.txt")
                .isFolder(false)
                .mimeType("text/plain")
                .sizeBytes(128L)
                .storageBucketName("smart-cloud-storage")
                .s3ObjectKey("users/" + userId + "/obj-1")
                .nodeStatus(FsNode.Status.ACTIVE)
                .build();

        when(fsNodeService.resolveAndValidateNode(userId, "25", true)).thenReturn(fileNode);
        when(minioStorageService.readObjectContent("smart-cloud-storage", "users/" + userId + "/obj-1"))
                .thenReturn("Hello from MinIO!");

        StreamObserver<ReadFileContentResponse> observer = mock(StreamObserver.class);
        grpcService.readFileContent(
                ReadFileContentRequest.newBuilder()
                        .setUserId(userId.toString())
                        .setNodeId("25")
                        .build(),
                observer
        );

        ArgumentCaptor<ReadFileContentResponse> captor = ArgumentCaptor.forClass(ReadFileContentResponse.class);
        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();

        assertEquals("Hello from MinIO!", captor.getValue().getContent());
        assertEquals("notes.txt", captor.getValue().getName());
    }

    @Test
    @SuppressWarnings("unchecked")
    void permanentDeleteNode_SoftDeletesActiveNodeThenPermanentlyDeletes() {
        UUID userId = UUID.randomUUID();
        FsUser user = FsUser.builder().id(userId).build();
        FsNode parentNode = FsNode.builder().id(1L).user(user).name("root").isFolder(true).build();
        FsNode activeNode = FsNode.builder()
                .id(77L)
                .user(user)
                .parent(parentNode)
                .name("confidential.pdf")
                .isFolder(false)
                .nodeStatus(FsNode.Status.ACTIVE)
                .build();

        when(fsNodeService.parseNodeId("77")).thenReturn(77L);
        when(fsNodeRepository.findById(77L)).thenReturn(Optional.of(activeNode));
        when(fsNodeService.isNodeDeleted(activeNode)).thenReturn(false);
        when(fsNodeTrashService.permanentDeleteTrashSync(userId, List.of("77")))
                .thenReturn(BatchOperationResponse.of(
                        1,
                        List.of(DeleteResult.builder().nodeId("77").operationId("op-1").build()),
                        Collections.emptyList()
                ));

        StreamObserver<PermanentDeleteNodeResponse> observer = mock(StreamObserver.class);
        grpcService.permanentDeleteNode(
                PermanentDeleteNodeRequest.newBuilder()
                        .setUserId(userId.toString())
                        .setNodeId("77")
                        .build(),
                observer
        );

        verify(fsNodeService).softDeleteNode(userId, "77");
        verify(fsNodeTrashService).permanentDeleteTrashSync(userId, List.of("77"));

        ArgumentCaptor<PermanentDeleteNodeResponse> captor = ArgumentCaptor.forClass(PermanentDeleteNodeResponse.class);
        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();
        assertEquals("77", captor.getValue().getNodeId());
        assertEquals(1, captor.getValue().getDeletedCount());
    }
}
