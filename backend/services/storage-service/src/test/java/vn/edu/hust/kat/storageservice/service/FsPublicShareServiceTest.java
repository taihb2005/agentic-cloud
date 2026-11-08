package vn.edu.hust.kat.storageservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.common.api.exception.ResourceNotFoundException;
import vn.edu.hust.kat.storageservice.config.MinioProperties;
import vn.edu.hust.kat.storageservice.dto.request.CreateFolderRequest;
import vn.edu.hust.kat.storageservice.dto.request.DownloadUrlRequest;
import vn.edu.hust.kat.storageservice.dto.request.FileUploadItemRequest;
import vn.edu.hust.kat.storageservice.dto.request.PublicAccessRequest;
import vn.edu.hust.kat.storageservice.dto.request.PublicDownloadRequest;
import vn.edu.hust.kat.storageservice.dto.request.UploadUrlRequest;
import vn.edu.hust.kat.storageservice.dto.response.*;
import vn.edu.hust.kat.storageservice.entity.FsNode;
import vn.edu.hust.kat.storageservice.entity.FsNodePermission;
import vn.edu.hust.kat.storageservice.entity.FsShare;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.exception.InvalidSharePasswordException;
import vn.edu.hust.kat.storageservice.exception.PasswordRequiredException;
import vn.edu.hust.kat.storageservice.exception.ShareExpiredException;
import vn.edu.hust.kat.storageservice.exception.ShareNotFoundException;
import vn.edu.hust.kat.storageservice.repository.FsNodeRepository;
import vn.edu.hust.kat.storageservice.repository.FsShareRepository;
import vn.edu.hust.kat.storageservice.repository.FsUserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FsPublicShareServiceTest {

    @Mock
    private FsShareRepository fsShareRepository;

    @Mock
    private FsNodeRepository fsNodeRepository;

    @Mock
    private FsNodeService fsNodeService;

    @Mock
    private FsNodeFolderService fsNodeFolderService;

    @Mock
    private FileUploadService fileUploadService;

    @Mock
    private FileDownloadService fileDownloadService;

    @Mock
    private MinioProperties minioProperties;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PublicShareTokenService publicShareTokenService;

    @InjectMocks
    private FsPublicShareService fsPublicShareService;

    private String shareToken;
    private FsUser owner;
    private FsNode sharedFolderNode;
    private FsNode sharedFileNode;
    private FsNodePermission viewPermission;
    private FsNodePermission editPermission;
    private FsShare publicShare;

    @BeforeEach
    void setUp() {
        shareToken = "tok_test_123456";
        owner = FsUser.builder()
                .id(UUID.randomUUID())
                .storagePrefix("users/alice/")
                .storageBucketName("test-bucket")
                .storageCurrentQuotaBytes(1000L)
                .storageMaxQuotaBytes(1000000L)
                .build();

        viewPermission = FsNodePermission.builder()
                .id(1L)
                .code(FsNodePermission.PermissionCode.VIEW)
                .build();

        editPermission = FsNodePermission.builder()
                .id(2L)
                .code(FsNodePermission.PermissionCode.EDIT)
                .build();

        sharedFolderNode = FsNode.builder()
                .id(5L)
                .name("Tai-lieu-chia-se")
                .isFolder(true)
                .materializedPath("root.5")
                .nodeStatus(FsNode.Status.ACTIVE)
                .user(owner)
                .build();

        sharedFileNode = FsNode.builder()
                .id(8L)
                .name("hop-dong.pdf")
                .isFolder(false)
                .materializedPath("root.8")
                .nodeStatus(FsNode.Status.ACTIVE)
                .mimeType("application/pdf")
                .sizeBytes(1048576L)
                .storageBucketName("test-bucket")
                .s3ObjectKey("users/alice/uuid-key")
                .user(owner)
                .build();

        publicShare = FsShare.builder()
                .id(UUID.randomUUID())
                .shareToken(shareToken)
                .shareMode(FsShare.ShareMode.PUBLIC_LINK)
                .isPublic(true)
                .permission(viewPermission)
                .node(sharedFolderNode)
                .status(FsShare.ShareStatus.ACTIVE)
                .isPasswordProtected(false)
                .accessCount(0)
                .createdBy(owner.getId())
                .build();

        lenient().when(fsNodeService.parseSort(any())).thenReturn(Sort.by("name").ascending());
        lenient().when(fsNodeService.parseNodeType(any())).thenReturn(null);
    }

    @Test
    @DisplayName("4.5.1. Xem thông tin link công khai thành công")
    void testGetPublicShareInfo_Success() {
        when(fsShareRepository.findByShareToken(shareToken)).thenReturn(Optional.of(publicShare));

        PublicShareInfoResponse info = fsPublicShareService.getPublicShareInfo(shareToken);

        assertNotNull(info);
        assertEquals(shareToken, info.getShareToken());
        assertEquals("Tai-lieu-chia-se", info.getNodeName());
        assertTrue(info.getIsFolder());
        assertEquals("VIEW", info.getPermission());
        assertFalse(info.getRequiresPassword());
        assertEquals("alice", info.getOwnerDisplayName());
    }

    @Test
    @DisplayName("4.5.2. Mở khóa link công khai thành công không có mật khẩu")
    void testAccessPublicShare_WithoutPassword_Success() {
        when(fsShareRepository.findByShareToken(shareToken)).thenReturn(Optional.of(publicShare));
        when(publicShareTokenService.generatePublicAccessToken(eq(shareToken), eq(sharedFolderNode.getId()), eq("VIEW"), anyLong()))
                .thenReturn("pub_sess_generated_token");
        when(fsShareRepository.save(any(FsShare.class))).thenReturn(publicShare);

        PublicAccessSessionResponse session = fsPublicShareService.accessPublicShare(shareToken, null);

        assertNotNull(session);
        assertEquals("pub_sess_generated_token", session.getPublicAccessToken());
        assertEquals("Bearer", session.getTokenType());
        assertEquals("VIEW", session.getPermission());
        assertEquals(1, publicShare.getAccessCount());
    }

    @Test
    @DisplayName("4.5.2. Mở khóa link công khai có mật khẩu: đúng mật khẩu thành công")
    void testAccessPublicShare_WithPassword_Success() {
        publicShare.setIsPasswordProtected(true);
        publicShare.setPasswordHash("hashed_secret");

        when(fsShareRepository.findByShareToken(shareToken)).thenReturn(Optional.of(publicShare));
        when(passwordEncoder.matches("MyPass123", "hashed_secret")).thenReturn(true);
        when(publicShareTokenService.generatePublicAccessToken(eq(shareToken), eq(sharedFolderNode.getId()), eq("VIEW"), anyLong()))
                .thenReturn("pub_sess_password_ok");
        when(fsShareRepository.save(any(FsShare.class))).thenReturn(publicShare);

        PublicAccessRequest req = PublicAccessRequest.builder().password("MyPass123").build();
        PublicAccessSessionResponse session = fsPublicShareService.accessPublicShare(shareToken, req);

        assertNotNull(session);
        assertEquals("pub_sess_password_ok", session.getPublicAccessToken());
    }

    @Test
    @DisplayName("4.5.2. Mở khóa link công khai có mật khẩu: thiếu mật khẩu ném PasswordRequiredException")
    void testAccessPublicShare_MissingPassword_ThrowsException() {
        publicShare.setIsPasswordProtected(true);
        publicShare.setPasswordHash("hashed_secret");

        when(fsShareRepository.findByShareToken(shareToken)).thenReturn(Optional.of(publicShare));

        assertThrows(PasswordRequiredException.class, () ->
                fsPublicShareService.accessPublicShare(shareToken, null));
    }

    @Test
    @DisplayName("4.5.2. Mở khóa link công khai có mật khẩu: sai mật khẩu ném InvalidSharePasswordException")
    void testAccessPublicShare_WrongPassword_ThrowsException() {
        publicShare.setIsPasswordProtected(true);
        publicShare.setPasswordHash("hashed_secret");

        when(fsShareRepository.findByShareToken(shareToken)).thenReturn(Optional.of(publicShare));
        when(passwordEncoder.matches("WrongPass", "hashed_secret")).thenReturn(false);

        PublicAccessRequest req = PublicAccessRequest.builder().password("WrongPass").build();
        assertThrows(InvalidSharePasswordException.class, () ->
                fsPublicShareService.accessPublicShare(shareToken, req));
    }

    @Test
    @DisplayName("4.5.2. Mở khóa link công khai: hết hạn ném ShareExpiredException")
    void testAccessPublicShare_Expired_ThrowsException() {
        publicShare.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(fsShareRepository.findByShareToken(shareToken)).thenReturn(Optional.of(publicShare));

        assertThrows(ShareExpiredException.class, () ->
                fsPublicShareService.accessPublicShare(shareToken, null));
    }

    @Test
    @DisplayName("4.5.2. Mở khóa link công khai: quá số lượt truy cập ném ShareExpiredException")
    void testAccessPublicShare_MaxAccessCountReached_ThrowsException() {
        publicShare.setMaxAccessCount(5);
        publicShare.setAccessCount(5);
        when(fsShareRepository.findByShareToken(shareToken)).thenReturn(Optional.of(publicShare));

        assertThrows(ShareExpiredException.class, () ->
                fsPublicShareService.accessPublicShare(shareToken, null));
    }

    @Test
    @DisplayName("4.5.3. Duyệt thư mục con qua link công khai tại thư mục gốc thành công")
    void testGetChildNodes_RootFolder_Success() {
        when(fsShareRepository.findByShareToken(shareToken)).thenReturn(Optional.of(publicShare));
        when(fsNodeService.parseNodeType(isNull())).thenReturn(null);
        when(fsNodeService.parseSort(any())).thenReturn(Sort.by("name").ascending());

        FsNode childFile = FsNode.builder()
                .id(10L)
                .name("child.pdf")
                .isFolder(false)
                .parent(sharedFolderNode)
                .user(owner)
                .materializedPath("root.5.10")
                .nodeStatus(FsNode.Status.ACTIVE)
                .build();

        when(fsNodeRepository.findChildNodesWithLtree(eq(owner.getId()), eq("root.5"), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(childFile)));

        PagedResponse<NodeResponse> response = fsPublicShareService.getChildNodes(
                shareToken, null, null, null, null, null, 0, 20, List.of("name,asc")
        );

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals("child.pdf", response.getContent().get(0).getName());
    }

    @Test
    @DisplayName("4.5.3. Duyệt thư mục con hợp lệ bên trong thư mục được chia sẻ thành công")
    void testGetChildNodes_ValidSubFolder_Success() {
        when(fsShareRepository.findByShareToken(shareToken)).thenReturn(Optional.of(publicShare));
        when(fsNodeService.parseNodeType(isNull())).thenReturn(null);
        when(fsNodeService.parseSort(any())).thenReturn(Sort.by("name").ascending());

        FsNode subFolder = FsNode.builder()
                .id(10L)
                .name("sub-folder")
                .isFolder(true)
                .parent(sharedFolderNode)
                .user(owner)
                .materializedPath("root.5.10")
                .nodeStatus(FsNode.Status.ACTIVE)
                .build();

        FsNode grandChild = FsNode.builder()
                .id(20L)
                .name("nested-file.txt")
                .isFolder(false)
                .parent(subFolder)
                .user(owner)
                .materializedPath("root.5.10.20")
                .nodeStatus(FsNode.Status.ACTIVE)
                .build();

        when(fsNodeRepository.findById(10L)).thenReturn(Optional.of(subFolder));
        when(fsNodeRepository.isDescendant(10L, 5L)).thenReturn(true);
        when(fsNodeRepository.findChildNodesWithLtree(eq(owner.getId()), eq("root.5.10"), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(grandChild)));

        PagedResponse<NodeResponse> response = fsPublicShareService.getChildNodes(
                shareToken, null, "10", null, null, null, 0, 20, List.of("name,asc")
        );

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals("nested-file.txt", response.getContent().get(0).getName());
    }

    @Test
    @DisplayName("4.5.3. Duyệt thư mục con có áp dụng bộ lọc node_type, name, mime_type")
    void testGetChildNodes_WithFiltersAndSort_Success() {
        when(fsShareRepository.findByShareToken(shareToken)).thenReturn(Optional.of(publicShare));
        when(fsNodeService.parseNodeType("FILE")).thenReturn(false);
        when(fsNodeService.parseSort(any())).thenReturn(Sort.by("name").descending());

        FsNode childFile = FsNode.builder()
                .id(15L)
                .name("hop-dong.pdf")
                .isFolder(false)
                .mimeType("application/pdf")
                .parent(sharedFolderNode)
                .user(owner)
                .materializedPath("root.5.15")
                .nodeStatus(FsNode.Status.ACTIVE)
                .build();

        when(fsNodeRepository.findChildNodesWithLtree(eq(owner.getId()), eq("root.5"), eq(false), eq("hop-dong"), eq("application/pdf"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(childFile)));

        PagedResponse<NodeResponse> response = fsPublicShareService.getChildNodes(
                shareToken, null, null, "FILE", "hop-dong", "application/pdf", 0, 10, List.of("name,desc")
        );

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals("hop-dong.pdf", response.getContent().get(0).getName());
    }

    @Test
    @DisplayName("4.5.3. Duyệt thư mục con: node chia sẻ là file ném BadRequestException")
    void testGetChildNodes_SharedNodeIsFile_ThrowsBadRequest() {
        publicShare.setNode(sharedFileNode);
        when(fsShareRepository.findByShareToken(shareToken)).thenReturn(Optional.of(publicShare));

        assertThrows(BadRequestException.class, () ->
                fsPublicShareService.getChildNodes(shareToken, null, null, null, null, null, 0, 20, List.of("name,asc")));
    }

    @Test
    @DisplayName("4.5.3. Duyệt thư mục con: parent_id không phải hậu duệ ném AccessDeniedException")
    void testGetChildNodes_NonDescendantParentId_ThrowsAccessDenied() {
        when(fsShareRepository.findByShareToken(shareToken)).thenReturn(Optional.of(publicShare));

        FsNode outsideFolder = FsNode.builder()
                .id(99L)
                .name("outside")
                .isFolder(true)
                .user(owner)
                .build();

        when(fsNodeRepository.findById(99L)).thenReturn(Optional.of(outsideFolder));
        when(fsNodeRepository.isDescendant(99L, 5L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () ->
                fsPublicShareService.getChildNodes(shareToken, null, "99", null, null, null, 0, 20, List.of("name,asc")));
    }

    @Test
    @DisplayName("4.5.3. Duyệt thư mục con: parent_id không tồn tại ném ResourceNotFoundException")
    void testGetChildNodes_SubFolderNotFound_ThrowsResourceNotFound() {
        when(fsShareRepository.findByShareToken(shareToken)).thenReturn(Optional.of(publicShare));
        when(fsNodeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                fsPublicShareService.getChildNodes(shareToken, null, "999", null, null, null, 0, 20, List.of("name,asc")));
    }

    @Test
    @DisplayName("4.5.3. Duyệt thư mục con: target không phải là thư mục ném BadRequestException")
    void testGetChildNodes_SubFolderNotFolder_ThrowsBadRequest() {
        when(fsShareRepository.findByShareToken(shareToken)).thenReturn(Optional.of(publicShare));

        FsNode fileNodeAsTarget = FsNode.builder()
                .id(10L)
                .name("not-a-folder.pdf")
                .isFolder(false)
                .user(owner)
                .nodeStatus(FsNode.Status.ACTIVE)
                .build();

        when(fsNodeRepository.findById(10L)).thenReturn(Optional.of(fileNodeAsTarget));
        when(fsNodeRepository.isDescendant(10L, 5L)).thenReturn(true);

        assertThrows(BadRequestException.class, () ->
                fsPublicShareService.getChildNodes(shareToken, null, "10", null, null, null, 0, 20, List.of("name,asc")));
    }

    @Test
    @DisplayName("4.5.3. Duyệt thư mục con: parent_id không phải định dạng số hợp lệ ném BadRequestException")
    void testGetChildNodes_InvalidParentIdFormat_ThrowsBadRequest() {
        when(fsShareRepository.findByShareToken(shareToken)).thenReturn(Optional.of(publicShare));

        assertThrows(BadRequestException.class, () ->
                fsPublicShareService.getChildNodes(shareToken, null, "invalid-id", null, null, null, 0, 20, List.of("name,asc")));
    }

    @Test
    @DisplayName("4.5.4. Tải tệp tin: link chia sẻ là file duy nhất thành công")
    void testDownloadFile_SingleFileShare_Success() {
        publicShare.setNode(sharedFileNode);
        when(fsShareRepository.findByShareToken(shareToken)).thenReturn(Optional.of(publicShare));
        when(minioProperties.getPresignedUrlExpirySeconds()).thenReturn(900);

        DownloadUrlsResponse downloadUrlsResponse = DownloadUrlsResponse.builder()
                .downloadUrls(List.of(
                        DownloadUrlItemResponse.builder()
                                .nodeId(String.valueOf(sharedFileNode.getId()))
                                .fileName(sharedFileNode.getName())
                                .url("http://minio/download/presigned")
                                .build()
                ))
                .build();

        when(fileDownloadService.getDownloadUrls(eq(owner.getId()), any(DownloadUrlRequest.class)))
                .thenReturn(downloadUrlsResponse);

        PresignedDownloadResponse res = fsPublicShareService.downloadFile(shareToken, null, null);

        assertNotNull(res);
        assertEquals(8L, res.getNodeId());
        assertEquals("hop-dong.pdf", res.getFileName());
        assertEquals("http://minio/download/presigned", res.getDownloadUrl());
        assertEquals(900, res.getExpiresInSeconds());
    }

    @Test
    @DisplayName("4.5.4. Tải tệp tin trong shared folder: truyền nodeId thành công")
    void testDownloadFile_InSharedFolder_Success() {
        when(fsShareRepository.findByShareToken(shareToken)).thenReturn(Optional.of(publicShare));

        FsNode childFile = FsNode.builder()
                .id(12L)
                .name("tailieu.pdf")
                .isFolder(false)
                .user(owner)
                .nodeStatus(FsNode.Status.ACTIVE)
                .build();

        when(fsNodeRepository.findById(12L)).thenReturn(Optional.of(childFile));
        when(fsNodeRepository.isDescendant(12L, 5L)).thenReturn(true);
        when(minioProperties.getPresignedUrlExpirySeconds()).thenReturn(900);

        DownloadUrlsResponse downloadUrlsResponse = DownloadUrlsResponse.builder()
                .downloadUrls(List.of(
                        DownloadUrlItemResponse.builder()
                                .nodeId("12")
                                .fileName("tailieu.pdf")
                                .url("http://minio/download/child12")
                                .build()
                ))
                .build();

        when(fileDownloadService.getDownloadUrls(eq(owner.getId()), any(DownloadUrlRequest.class)))
                .thenReturn(downloadUrlsResponse);

        PublicDownloadRequest req = PublicDownloadRequest.builder().nodeId(12L).build();
        PresignedDownloadResponse res = fsPublicShareService.downloadFile(shareToken, null, req);

        assertNotNull(res);
        assertEquals(12L, res.getNodeId());
        assertEquals("tailieu.pdf", res.getFileName());
        assertEquals("http://minio/download/child12", res.getDownloadUrl());
    }

    @Test
    @DisplayName("4.5.5.1. Tạo folder con: thành công khi link có quyền EDIT")
    void testCreateFolder_SuccessWhenEditPermission() {
        publicShare.setPermission(editPermission);
        when(fsShareRepository.findByShareToken(shareToken)).thenReturn(Optional.of(publicShare));
        when(fsNodeRepository.isDescendant(5L, 5L)).thenReturn(true);

        NodeResponse nodeResponse = NodeResponse.builder()
                .id(20L)
                .name("Thu-muc-moi")
                .isFolder(true)
                .build();

        when(fsNodeFolderService.createFolder(eq(owner.getId()), eq("5"), eq("Thu-muc-moi")))
                .thenReturn(nodeResponse);

        CreateFolderRequest req = CreateFolderRequest.builder().name("Thu-muc-moi").parentId("5").build();
        NodeResponse res = fsPublicShareService.createFolder(shareToken, null, req);

        assertNotNull(res);
        assertEquals(20L, res.getId());
        assertEquals("Thu-muc-moi", res.getName());
    }

    @Test
    @DisplayName("4.5.5.1. Tạo folder con: ném AccessDeniedException khi link chỉ có quyền VIEW")
    void testCreateFolder_ThrowsWhenViewOnly() {
        publicShare.setPermission(viewPermission);
        when(fsShareRepository.findByShareToken(shareToken)).thenReturn(Optional.of(publicShare));

        CreateFolderRequest req = CreateFolderRequest.builder().name("Thu-muc-moi").build();
        assertThrows(AccessDeniedException.class, () ->
                fsPublicShareService.createFolder(shareToken, null, req));
    }

    @Test
    @DisplayName("4.5.5.2. Xin URL upload: ném AccessDeniedException khi link chỉ có quyền VIEW")
    void testGetUploadUrls_ThrowsWhenViewOnly() {
        publicShare.setPermission(viewPermission);
        when(fsShareRepository.findByShareToken(shareToken)).thenReturn(Optional.of(publicShare));

        UploadUrlRequest req = UploadUrlRequest.builder()
                .files(List.of(FileUploadItemRequest.builder().name("f.txt").size(10L).hash("h").build()))
                .build();

        assertThrows(AccessDeniedException.class, () ->
                fsPublicShareService.getUploadUrls(shareToken, null, req));
    }

    @Test
    @DisplayName("4.5.5.2. Xin URL upload: thành công khi link có quyền EDIT")
    void testGetUploadUrls_SuccessWhenEditPermission() {
        publicShare.setPermission(editPermission);
        when(fsShareRepository.findByShareToken(shareToken)).thenReturn(Optional.of(publicShare));

        UploadUrlsResponse expectedResponse = UploadUrlsResponse.builder()
                .presignedUrls(List.of(
                        PresignedUrlResponse.builder()
                                .nodeId("30")
                                .url("http://minio/upload/put")
                                .instantUploaded(false)
                                .build()
                ))
                .build();

        when(fileUploadService.getUploadUrls(eq(owner.getId()), any(UploadUrlRequest.class)))
                .thenReturn(expectedResponse);

        UploadUrlRequest req = UploadUrlRequest.builder()
                .files(List.of(
                        FileUploadItemRequest.builder()
                                .name("file.txt")
                                .size(500L)
                                .hash("hash123")
                                .build()
                ))
                .build();

        UploadUrlsResponse res = fsPublicShareService.getUploadUrls(shareToken, null, req);

        assertNotNull(res);
        assertEquals(1, res.getPresignedUrls().size());
        assertEquals("30", res.getPresignedUrls().get(0).getNodeId());
        assertEquals("http://minio/upload/put", res.getPresignedUrls().get(0).getUrl());
        assertFalse(res.getPresignedUrls().get(0).getInstantUploaded());
    }
}
