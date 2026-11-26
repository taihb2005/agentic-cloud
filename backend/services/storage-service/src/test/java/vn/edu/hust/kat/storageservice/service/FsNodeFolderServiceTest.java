package vn.edu.hust.kat.storageservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.storageservice.dto.response.MinioObjectResponse;
import vn.edu.hust.kat.storageservice.dto.response.RecursiveFolderCreationResponse;
import vn.edu.hust.kat.storageservice.entity.FsNode;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.repository.FsNodeRepository;
import vn.edu.hust.kat.storageservice.repository.FsUserRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FsNodeFolderServiceTest {

    @Mock
    private FsUserRepository fsUserRepository;

    @Mock
    private FsNodeRepository fsNodeRepository;

    @Mock
    private FsNodeService fsNodeService;

    @Mock
    private MinioStorageService minioStorageService;

    @InjectMocks
    private FsNodeFolderService fsNodeFolderService;

    private UUID userId;
    private FsUser user;
    private FsNode rootNode;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = FsUser.builder()
                .id(userId)
                .storageBucketName("smart-cloud-bucket")
                .storagePrefix("users/" + userId)
                .build();

        rootNode = FsNode.builder()
                .id(1L)
                .user(user)
                .parent(null)
                .name("root")
                .isFolder(true)
                .materializedPath("root")
                .nodeStatus(FsNode.Status.ACTIVE)
                .build();
    }

    @Test
    void createFolderRecursive_CreatesAllLevelsWhenNoneExist() {
        when(fsUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(fsNodeService.resolveAndValidateNode(userId, "root", true)).thenReturn(rootNode);
        when(fsNodeRepository.findActiveNodeByParentIdAndName(eq(userId), anyLong(), anyString()))
                .thenReturn(Optional.empty());

        AtomicLong idCounter = new AtomicLong(10L);
        when(fsNodeRepository.save(any(FsNode.class))).thenAnswer(inv -> {
            FsNode node = inv.getArgument(0);
            if (node.getId() == null) {
                node.setId(idCounter.getAndIncrement());
            }
            return node;
        });

        when(minioStorageService.createDirectory(eq("smart-cloud-bucket"), anyString()))
                .thenAnswer(inv -> MinioObjectResponse.builder()
                        .bucketName("smart-cloud-bucket")
                        .objectKey("users/" + userId + "/" + UUID.randomUUID() + "/")
                        .etag("\"etag-dir\"")
                        .build());

        RecursiveFolderCreationResponse result = fsNodeFolderService.createFolderRecursive(
                userId,
                "root",
                "Du-an-2026/Tai-lieu/Bao-cao-Q1"
        );

        assertNotNull(result);
        assertEquals(3, result.getCreatedCount());
        assertEquals(0, result.getReusedCount());
        assertEquals(10L, result.getFirstCreatedNodeId());
        assertNotNull(result.getLeafNode());
        assertEquals(12L, result.getLeafNode().getId());
        assertEquals(11L, result.getLeafNode().getParentId());
        assertEquals("Bao-cao-Q1", result.getLeafNode().getName());
        assertEquals("root.10.11.12", result.getLeafNode().getPath());
        verify(minioStorageService, times(3)).createDirectory(eq("smart-cloud-bucket"), anyString());
    }

    @Test
    void createFolderRecursive_ReusesExistingParentFolders_AndRecordsFirstNewlyCreatedNodeId() {
        when(fsUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(fsNodeService.resolveAndValidateNode(userId, "root", true)).thenReturn(rootNode);

        FsNode existingFolderA = FsNode.builder()
                .id(50L)
                .user(user)
                .parent(rootNode)
                .name("Du-an-2026")
                .isFolder(true)
                .materializedPath("root.50")
                .nodeStatus(FsNode.Status.ACTIVE)
                .build();

        when(fsNodeRepository.findActiveNodeByParentIdAndName(userId, 1L, "Du-an-2026"))
                .thenReturn(Optional.of(existingFolderA));
        when(fsNodeRepository.findActiveNodeByParentIdAndName(userId, 50L, "Tai-lieu"))
                .thenReturn(Optional.empty());
        when(fsNodeRepository.findActiveNodeByParentIdAndName(userId, 51L, "Bao-cao-Q1"))
                .thenReturn(Optional.empty());

        AtomicLong idCounter = new AtomicLong(51L);
        when(fsNodeRepository.save(any(FsNode.class))).thenAnswer(inv -> {
            FsNode node = inv.getArgument(0);
            if (node.getId() == null) {
                node.setId(idCounter.getAndIncrement());
            }
            return node;
        });

        when(minioStorageService.createDirectory(eq("smart-cloud-bucket"), anyString()))
                .thenAnswer(inv -> MinioObjectResponse.builder()
                        .bucketName("smart-cloud-bucket")
                        .objectKey("users/" + userId + "/" + UUID.randomUUID() + "/")
                        .etag("\"etag-dir\"")
                        .build());

        RecursiveFolderCreationResponse result = fsNodeFolderService.createFolderRecursive(
                userId,
                "root",
                "/Du-an-2026/Tai-lieu/Bao-cao-Q1/"
        );

        assertEquals(2, result.getCreatedCount());
        assertEquals(1, result.getReusedCount());
        assertEquals(51L, result.getFirstCreatedNodeId());
        assertEquals(52L, result.getLeafNode().getId());
        assertEquals("root.50.51.52", result.getLeafNode().getPath());
        verify(minioStorageService, times(2)).createDirectory(eq("smart-cloud-bucket"), anyString());
    }

    @Test
    void createFolderRecursive_ThrowsWhenIntermediateNodeIsFile() {
        when(fsUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(fsNodeService.resolveAndValidateNode(userId, "root", true)).thenReturn(rootNode);

        FsNode existingFile = FsNode.builder()
                .id(60L)
                .user(user)
                .parent(rootNode)
                .name("readme.txt")
                .isFolder(false)
                .materializedPath("root.60")
                .nodeStatus(FsNode.Status.ACTIVE)
                .build();

        when(fsNodeRepository.findActiveNodeByParentIdAndName(userId, 1L, "readme.txt"))
                .thenReturn(Optional.of(existingFile));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                fsNodeFolderService.createFolderRecursive(userId, "root", "readme.txt/SubFolder")
        );
        assertTrue(ex.getMessage().contains("tệp tin"));
    }

    @Test
    void createFolderRecursive_ThrowsAndCleansUpMinioObjects_WhenLeafAlreadyExistsOrErrorOccurs() {
        when(fsUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(fsNodeService.resolveAndValidateNode(userId, "root", true)).thenReturn(rootNode);

        when(fsNodeRepository.findActiveNodeByParentIdAndName(userId, 1L, "NewParent"))
                .thenReturn(Optional.empty());

        AtomicLong idCounter = new AtomicLong(70L);
        when(fsNodeRepository.save(any(FsNode.class))).thenAnswer(inv -> {
            FsNode node = inv.getArgument(0);
            if (node.getId() == null) {
                node.setId(idCounter.getAndIncrement());
            }
            return node;
        });

        when(minioStorageService.createDirectory(eq("smart-cloud-bucket"), anyString()))
                .thenReturn(MinioObjectResponse.builder()
                        .bucketName("smart-cloud-bucket")
                        .objectKey("users/" + userId + "/created-key-1/")
                        .etag("\"etag-1\"")
                        .build())
                .thenThrow(new RuntimeException("MinIO connection lost"));

        when(fsNodeRepository.findActiveNodeByParentIdAndName(userId, 70L, "ChildFail"))
                .thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () ->
                fsNodeFolderService.createFolderRecursive(userId, "root", "NewParent/ChildFail")
        );

        verify(minioStorageService).deleteObjectSilently("smart-cloud-bucket", "users/" + userId + "/created-key-1/");
    }

    @Test
    void createFolderRecursive_ThrowsWhenDepthExceedsMaxLimit10() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                fsNodeFolderService.createFolderRecursive(
                        userId,
                        "root",
                        "L1/L2/L3/L4/L5/L6/L7/L8/L9/L10/L11"
                )
        );
        assertTrue(ex.getMessage().contains("10"));
    }
}
