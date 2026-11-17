package vn.edu.hust.kat.storageservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.hust.kat.storageservice.config.MinioProperties;
import vn.edu.hust.kat.storageservice.entity.FsNode;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.repository.FsNodeRepository;
import vn.edu.hust.kat.storageservice.repository.FsNodeShareUserRepository;
import vn.edu.hust.kat.storageservice.repository.FsShareRepository;
import vn.edu.hust.kat.storageservice.repository.FsUserRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageUserServiceTest {

    @Mock
    private FsUserRepository fsUserRepository;

    @Mock
    private FsNodeRepository fsNodeRepository;

    @Mock
    private FsShareRepository fsShareRepository;

    @Mock
    private FsNodeShareUserRepository fsNodeShareUserRepository;

    @Mock
    private FsNodeService fsNodeService;

    @Mock
    private MinioStorageService minioStorageService;

    @Mock
    private MinioProperties minioProperties;

    @InjectMocks
    private StorageUserService storageUserService;

    private UUID userId;
    private String bucketName;
    private String storagePrefix;
    private FsUser fsUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        bucketName = "smart-cloud-storage";
        storagePrefix = "users/" + userId + "/";

        fsUser = FsUser.builder()
                .id(userId)
                .storageBucketName(bucketName)
                .storagePrefix(storagePrefix)
                .storageMaxQuotaBytes(10737418240L)
                .storageCurrentQuotaBytes(2048L)
                .build();
    }

    @Test
    void deleteUserStorage_HardDeletesDbAndRetainsDeduplicatedMinioObjects() {
        String rootFolderKey = storagePrefix + "root-uuid/";
        String privateFileKey = storagePrefix + "private-file-uuid";
        String deduplicatedFileKey = storagePrefix + "shared-hash-file-uuid";
        String orphanPrefixKey = storagePrefix + "orphan-file-uuid";

        FsNode rootNode = FsNode.builder()
                .id(1L)
                .user(fsUser)
                .isFolder(true)
                .storageBucketName(bucketName)
                .s3ObjectKey(rootFolderKey)
                .build();

        FsNode privateFileNode = FsNode.builder()
                .id(2L)
                .user(fsUser)
                .isFolder(false)
                .storageBucketName(bucketName)
                .s3ObjectKey(privateFileKey)
                .build();

        FsNode deduplicatedFileNode = FsNode.builder()
                .id(3L)
                .user(fsUser)
                .isFolder(false)
                .storageBucketName(bucketName)
                .s3ObjectKey(deduplicatedFileKey)
                .build();

        when(fsUserRepository.findById(userId)).thenReturn(Optional.of(fsUser));
        when(fsNodeRepository.findAllByUserId(userId))
                .thenReturn(List.of(rootNode, privateFileNode, deduplicatedFileNode));
        when(minioStorageService.listObjectKeysByPrefix(bucketName, storagePrefix))
                .thenReturn(List.of(rootFolderKey, privateFileKey, deduplicatedFileKey, orphanPrefixKey));

        // deduplicatedFileKey đang được user khác tham chiếu
        when(fsNodeRepository.findObjectKeysReferencedByOtherUsers(eq(userId), anyCollection()))
                .thenReturn(Set.of(deduplicatedFileKey));

        storageUserService.deleteUserStorage(userId);

        // Kiểm tra thứ tự xóa cứng trong DB
        InOrder inOrder = inOrder(
                fsNodeShareUserRepository,
                fsShareRepository,
                fsNodeRepository,
                fsUserRepository
        );
        inOrder.verify(fsNodeShareUserRepository).deleteAllByUserIdOrNodeOwnerId(userId);
        inOrder.verify(fsShareRepository).deleteAllByUserIdOrNodeOwnerId(userId);
        inOrder.verify(fsNodeRepository).deleteAllByUserId(userId);
        inOrder.verify(fsUserRepository).deleteHardById(userId);

        // Kiểm tra xóa các object riêng của user trên MinIO
        verify(minioStorageService).deleteObjectSilently(bucketName, rootFolderKey);
        verify(minioStorageService).deleteObjectSilently(bucketName, privateFileKey);
        verify(minioStorageService).deleteObjectSilently(bucketName, orphanPrefixKey);

        // Không được xóa file đang được user khác tham chiếu do deduplication
        verify(minioStorageService, never()).deleteObjectSilently(bucketName, deduplicatedFileKey);
    }
}
