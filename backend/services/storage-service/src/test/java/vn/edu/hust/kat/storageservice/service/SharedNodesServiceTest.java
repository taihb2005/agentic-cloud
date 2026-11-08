package vn.edu.hust.kat.storageservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.common.api.exception.ResourceNotFoundException;
import vn.edu.hust.kat.storageservice.dto.response.EffectiveAccessResponse;
import vn.edu.hust.kat.storageservice.dto.response.PagedResponse;
import vn.edu.hust.kat.storageservice.dto.response.SharedWithMeNodeResponse;
import vn.edu.hust.kat.storageservice.entity.FsNode;
import vn.edu.hust.kat.storageservice.entity.FsNodePermission;
import vn.edu.hust.kat.storageservice.entity.FsNodeShareUser;
import vn.edu.hust.kat.storageservice.entity.FsShare;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.repository.FsNodeRepository;
import vn.edu.hust.kat.storageservice.repository.FsNodeShareUserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SharedNodesServiceTest {

    @Mock
    private FsNodeRepository fsNodeRepository;

    @Mock
    private FsNodeShareUserRepository fsNodeShareUserRepository;

    @Mock
    private PublicShareTokenService publicShareTokenService;

    @InjectMocks
    private SharedNodesService sharedNodesService;

    private UUID ownerId;
    private UUID bobUserId;
    private FsUser ownerUser;
    private FsNodePermission viewPermission;
    private FsNodePermission editPermission;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        bobUserId = UUID.randomUUID();
        ownerUser = FsUser.builder().id(ownerId).storagePrefix("users/alice").build();

        viewPermission = FsNodePermission.builder()
                .id(1L)
                .code(FsNodePermission.PermissionCode.VIEW)
                .build();

        editPermission = FsNodePermission.builder()
                .id(2L)
                .code(FsNodePermission.PermissionCode.EDIT)
                .build();
    }

    @Test
    @DisplayName("getSharedWithMe - Lấy danh sách chia sẻ thành công")
    void testGetSharedWithMe_Success() {
        FsShare shareWithPassword = FsShare.builder().isPasswordProtected(true).build();
        FsShare shareNoPassword = FsShare.builder().isPasswordProtected(false).build();

        FsNode folderNode = FsNode.builder()
                .id(5L)
                .name("Tai-lieu-du-an-2026")
                .isFolder(true)
                .user(ownerUser)
                .share(shareWithPassword)
                .build();

        FsNode fileNode = FsNode.builder()
                .id(14L)
                .name("Thiet-ke-kien-truc.docx")
                .isFolder(false)
                .user(ownerUser)
                .share(shareNoPassword)
                .mimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .sizeBytes(524288L)
                .build();

        FsNodeShareUser su1 = FsNodeShareUser.builder()
                .node(folderNode)
                .userId(bobUserId)
                .permission(viewPermission)
                .createdAt(LocalDateTime.now())
                .build();

        FsNodeShareUser su2 = FsNodeShareUser.builder()
                .node(fileNode)
                .userId(bobUserId)
                .permission(editPermission)
                .createdAt(LocalDateTime.now())
                .build();

        Page<FsNodeShareUser> page = new PageImpl<>(List.of(su1, su2));
        when(fsNodeShareUserRepository.findSharedWithMe(eq(bobUserId), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);

        PagedResponse<SharedWithMeNodeResponse> result = sharedNodesService.getSharedWithMe(
                bobUserId, null, null, null, 0, 20, "shared_at,desc", null
        );

        assertNotNull(result);
        assertEquals(2, result.getContent().size());

        SharedWithMeNodeResponse r1 = result.getContent().get(0);
        assertEquals(5L, r1.getNodeId());
        assertEquals("Tai-lieu-du-an-2026", r1.getName());
        assertTrue(r1.getIsFolder());
        assertTrue(r1.getIsPasswordProtected());
        assertFalse(r1.getIsUnlocked()); // no token provided

        SharedWithMeNodeResponse r2 = result.getContent().get(1);
        assertEquals(14L, r2.getNodeId());
        assertFalse(r2.getIsFolder());
        assertFalse(r2.getIsPasswordProtected());
        assertTrue(r2.getIsUnlocked());
        assertEquals("EDIT", r2.getPermission());
    }

    @Test
    @DisplayName("getSharedWithMe - node_type không hợp lệ ném BadRequestException")
    void testGetSharedWithMe_InvalidNodeType() {
        assertThrows(BadRequestException.class, () ->
                sharedNodesService.getSharedWithMe(bobUserId, "INVALID", null, null, 0, 20, null, null)
        );
    }

    @Test
    @DisplayName("getSharedWithMe - permission không hợp lệ ném BadRequestException")
    void testGetSharedWithMe_InvalidPermission() {
        assertThrows(BadRequestException.class, () ->
                sharedNodesService.getSharedWithMe(bobUserId, null, "ADMIN", null, 0, 20, null, null)
        );
    }

    @Test
    @DisplayName("checkEffectiveAccess - Quyền Chủ sở hữu (Owner)")
    void testCheckEffectiveAccess_Owner() {
        FsNode node = FsNode.builder()
                .id(10L)
                .name("MyProjectFolder")
                .isFolder(true)
                .user(ownerUser)
                .build();

        when(fsNodeRepository.findById(10L)).thenReturn(Optional.of(node));

        EffectiveAccessResponse response = sharedNodesService.checkEffectiveAccess(ownerId, 10L, null);

        assertNotNull(response);
        assertEquals(10L, response.getNodeId());
        assertTrue(response.getIsOwner());
        assertEquals("OWNER", response.getEffectivePermission());
        assertFalse(response.getIsInherited());
        assertTrue(response.getIsUnlocked());
        assertTrue(response.getCanView());
        assertTrue(response.getCanEdit());
    }

    @Test
    @DisplayName("checkEffectiveAccess - Quyền trực tiếp (Direct Share)")
    void testCheckEffectiveAccess_DirectShare() {
        FsNode node = FsNode.builder()
                .id(5L)
                .name("SharedFolder")
                .isFolder(true)
                .user(ownerUser)
                .share(FsShare.builder().isPasswordProtected(false).build())
                .build();

        FsNodeShareUser directShare = FsNodeShareUser.builder()
                .node(node)
                .userId(bobUserId)
                .permission(viewPermission)
                .build();

        when(fsNodeRepository.findById(5L)).thenReturn(Optional.of(node));
        when(fsNodeShareUserRepository.findByNodeIdAndUserId(5L, bobUserId)).thenReturn(Optional.of(directShare));

        EffectiveAccessResponse response = sharedNodesService.checkEffectiveAccess(bobUserId, 5L, null);

        assertNotNull(response);
        assertFalse(response.getIsOwner());
        assertEquals("VIEW", response.getEffectivePermission());
        assertFalse(response.getIsInherited());
        assertFalse(response.getIsPasswordProtected());
        assertTrue(response.getIsUnlocked());
        assertTrue(response.getCanView());
        assertFalse(response.getCanEdit());
    }

    @Test
    @DisplayName("checkEffectiveAccess - Quyền kế thừa từ thư mục cha (Inherited Share)")
    void testCheckEffectiveAccess_InheritedShare() {
        FsShare folderShare = FsShare.builder()
                .isPasswordProtected(true)
                .passwordHash("hash")
                .build();

        FsNode parentFolder = FsNode.builder()
                .id(5L)
                .name("Tai-lieu-du-an-2026")
                .isFolder(true)
                .user(ownerUser)
                .share(folderShare)
                .build();

        FsNode childFile = FsNode.builder()
                .id(12L)
                .name("Ban-ve-CAD.dwg")
                .isFolder(false)
                .user(ownerUser)
                .parent(parentFolder)
                .build();

        FsNodeShareUser ancestorShare = FsNodeShareUser.builder()
                .node(parentFolder)
                .userId(bobUserId)
                .permission(viewPermission)
                .build();

        when(fsNodeRepository.findById(12L)).thenReturn(Optional.of(childFile));
        when(fsNodeShareUserRepository.findByNodeIdAndUserId(12L, bobUserId)).thenReturn(Optional.empty());
        when(fsNodeShareUserRepository.findByNodeIdAndUserId(5L, bobUserId)).thenReturn(Optional.of(ancestorShare));
        when(publicShareTokenService.isNodeUnlockedByUserToken(eq("valid_token"), eq(bobUserId), any(FsNode.class), eq(5L), any()))
                .thenReturn(true);

        EffectiveAccessResponse response = sharedNodesService.checkEffectiveAccess(bobUserId, 12L, "valid_token");

        assertNotNull(response);
        assertEquals(12L, response.getNodeId());
        assertEquals("Ban-ve-CAD.dwg", response.getNodeName());
        assertFalse(response.getIsOwner());
        assertEquals("VIEW", response.getEffectivePermission());
        assertTrue(response.getIsInherited());
        assertEquals(5L, response.getInheritedFromNodeId());
        assertEquals("Tai-lieu-du-an-2026", response.getInheritedFromNodeName());
        assertTrue(response.getIsPasswordProtected());
        assertTrue(response.getIsUnlocked());
        assertTrue(response.getCanView());
        assertFalse(response.getCanEdit());
    }

    @Test
    @DisplayName("checkEffectiveAccess - Không có quyền truy cập ném AccessDeniedException")
    void testCheckEffectiveAccess_PermissionDenied() {
        FsNode node = FsNode.builder()
                .id(10L)
                .name("PrivateFolder")
                .isFolder(true)
                .user(ownerUser)
                .build();

        when(fsNodeRepository.findById(10L)).thenReturn(Optional.of(node));
        when(fsNodeShareUserRepository.findByNodeIdAndUserId(10L, bobUserId)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () ->
                sharedNodesService.checkEffectiveAccess(bobUserId, 10L, null)
        );
    }
}
