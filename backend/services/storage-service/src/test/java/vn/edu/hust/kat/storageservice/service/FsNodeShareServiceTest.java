package vn.edu.hust.kat.storageservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.common.api.exception.DuplicateResourceException;
import vn.edu.hust.kat.storageservice.dto.request.ConfigureShareRequest;
import vn.edu.hust.kat.storageservice.dto.request.UpdateUserGroupShareRequest;
import vn.edu.hust.kat.storageservice.dto.response.NodeShareDetailResponse;
import vn.edu.hust.kat.storageservice.entity.*;
import vn.edu.hust.kat.storageservice.repository.*;
import vn.edu.hust.kat.storageservice.utils.ShareTokenGenerator;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FsNodeShareServiceTest {

    @Mock
    private FsNodeRepository fsNodeRepository;

    @Mock
    private FsShareRepository fsShareRepository;

    @Mock
    private FsNodeShareUserRepository fsNodeShareUserRepository;

    @Mock
    private FsNodePermissionRepository fsNodePermissionRepository;

    @Mock
    private FsUserRepository fsUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ShareTokenGenerator shareTokenGenerator;

    @InjectMocks
    private FsNodeShareService fsNodeShareService;

    private UUID ownerId;
    private UUID targetUserId;
    private Long nodeId;
    private FsUser ownerUser;
    private FsNode testNode;
    private FsNodePermission viewPermission;
    private FsNodePermission editPermission;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        targetUserId = UUID.randomUUID();
        nodeId = 100L;

        ownerUser = FsUser.builder().id(ownerId).storagePrefix("users/owner").build();
        testNode = FsNode.builder()
                .id(nodeId)
                .name("Tai-lieu-test")
                .isFolder(true)
                .user(ownerUser)
                .build();

        viewPermission = FsNodePermission.builder()
                .id(1L)
                .code(FsNodePermission.PermissionCode.VIEW)
                .description("Quyền xem")
                .build();

        editPermission = FsNodePermission.builder()
                .id(2L)
                .code(FsNodePermission.PermissionCode.EDIT)
                .description("Quyền chỉnh sửa")
                .build();
    }

    @Test
    @DisplayName("Cấu hình chia sẻ nhóm thành công với 1 quyền duy nhất VIEW")
    void testConfigureShare_UserGroup_Success() {
        ConfigureShareRequest request = ConfigureShareRequest.builder()
                .shareMode("USER_GROUP")
                .permission("VIEW")
                .userIds(List.of(targetUserId))
                .build();

        when(fsNodeRepository.findById(nodeId)).thenReturn(Optional.of(testNode));
        when(fsNodePermissionRepository.findByCodeString("VIEW")).thenReturn(Optional.of(viewPermission));
        when(fsShareRepository.findByNodeId(nodeId)).thenReturn(Optional.empty());
        when(fsShareRepository.save(any(FsShare.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NodeShareDetailResponse response = fsNodeShareService.configureShare(ownerId, nodeId, request);

        assertNotNull(response);
        assertEquals(nodeId, response.getNodeId());
        assertEquals("USER_GROUP", response.getShareMode());
        assertEquals("VIEW", response.getPermission());
        assertFalse(response.getIsPasswordProtected());

        verify(fsNodeShareUserRepository).deleteByNodeId(nodeId);
        verify(fsNodeShareUserRepository).save(argThat(u ->
                u.getUserId().equals(targetUserId) && u.getPermission().equals(viewPermission)
        ));
    }

    @Test
    @DisplayName("Từ chối khi không phải Chủ sở hữu (Owner) gọi cấu hình chia sẻ")
    void testConfigureShare_NonOwner_ThrowsAccessDenied() {
        UUID strangerId = UUID.randomUUID();
        ConfigureShareRequest request = ConfigureShareRequest.builder()
                .shareMode("USER_GROUP")
                .permission("VIEW")
                .userIds(List.of(targetUserId))
                .build();

        when(fsNodeRepository.findById(nodeId)).thenReturn(Optional.of(testNode));

        assertThrows(AccessDeniedException.class, () ->
                fsNodeShareService.configureShare(strangerId, nodeId, request)
        );
    }

    @Test
    @DisplayName("Từ chối khi chia sẻ file/folder cho chính mình")
    void testConfigureShare_ShareWithSelf_ThrowsBadRequest() {
        ConfigureShareRequest request = ConfigureShareRequest.builder()
                .shareMode("USER_GROUP")
                .permission("VIEW")
                .userIds(List.of(ownerId))
                .build();

        when(fsNodeRepository.findById(nodeId)).thenReturn(Optional.of(testNode));
        when(fsNodePermissionRepository.findByCodeString("VIEW")).thenReturn(Optional.of(viewPermission));

        assertThrows(BadRequestException.class, () ->
                fsNodeShareService.configureShare(ownerId, nodeId, request)
        );
    }

    @Test
    @DisplayName("Kiểm tra loại trừ lẫn nhau: Node đang có Public link thì không được chia sẻ nhóm nếu overrideExisting=false")
    void testConfigureShare_Conflict_PublicExists() {
        FsShare publicShare = FsShare.builder()
                .node(testNode)
                .shareMode(FsShare.ShareMode.PUBLIC_LINK)
                .isPublic(true)
                .build();

        ConfigureShareRequest request = ConfigureShareRequest.builder()
                .shareMode("USER_GROUP")
                .permission("VIEW")
                .userIds(List.of(targetUserId))
                .overrideExisting(false)
                .build();

        when(fsNodeRepository.findById(nodeId)).thenReturn(Optional.of(testNode));
        when(fsNodePermissionRepository.findByCodeString("VIEW")).thenReturn(Optional.of(viewPermission));
        when(fsShareRepository.findByNodeId(nodeId)).thenReturn(Optional.of(publicShare));

        assertThrows(DuplicateResourceException.class, () ->
                fsNodeShareService.configureShare(ownerId, nodeId, request)
        );
    }

    @Test
    @DisplayName("Cấu hình chia sẻ có mật khẩu: Mật khẩu được mã hóa an toàn qua PasswordEncoder")
    void testConfigureShare_WithPassword_Success() {
        ConfigureShareRequest request = ConfigureShareRequest.builder()
                .shareMode("USER_GROUP")
                .permission("EDIT")
                .userIds(List.of(targetUserId))
                .password("SecureSecret123!")
                .build();

        when(fsNodeRepository.findById(nodeId)).thenReturn(Optional.of(testNode));
        when(fsNodePermissionRepository.findByCodeString("EDIT")).thenReturn(Optional.of(editPermission));
        when(fsShareRepository.findByNodeId(nodeId)).thenReturn(Optional.empty());
        when(passwordEncoder.encode("SecureSecret123!")).thenReturn("bcrypt_hashed_string");
        when(fsShareRepository.save(any(FsShare.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NodeShareDetailResponse response = fsNodeShareService.configureShare(ownerId, nodeId, request);

        assertTrue(response.getIsPasswordProtected());
        verify(passwordEncoder).encode("SecureSecret123!");
    }

    @Test
    @DisplayName("Hủy toàn bộ chia sẻ (revokeAllShare) đưa node về trạng thái riêng tư PRIVATE")
    void testRevokeAllShare_Success() {
        FsShare existingShare = FsShare.builder()
                .node(testNode)
                .shareMode(FsShare.ShareMode.USER_GROUP)
                .status(FsShare.ShareStatus.ACTIVE)
                .build();

        when(fsNodeRepository.findById(nodeId)).thenReturn(Optional.of(testNode));
        when(fsShareRepository.findByNodeId(nodeId)).thenReturn(Optional.of(existingShare));

        fsNodeShareService.revokeAllShare(ownerId, nodeId);

        verify(fsNodeShareUserRepository).deleteByNodeId(nodeId);
        verify(fsShareRepository).save(argThat(s ->
                s.getShareMode() == FsShare.ShareMode.NONE &&
                s.getStatus() == FsShare.ShareStatus.REVOKED &&
                !Boolean.TRUE.equals(s.getIsPublic())
        ));
    }

    @Test
    @DisplayName("Cập nhật nhóm user với action CHANGE_PERMISSION đổi đồng bộ quyền cho nhóm")
    void testUpdateUserGroup_ChangePermission() {
        FsShare share = FsShare.builder()
                .node(testNode)
                .shareMode(FsShare.ShareMode.USER_GROUP)
                .permission(viewPermission)
                .build();

        FsNodeShareUser shareUser = FsNodeShareUser.builder()
                .node(testNode)
                .userId(targetUserId)
                .permission(viewPermission)
                .build();

        UpdateUserGroupShareRequest request = UpdateUserGroupShareRequest.builder()
                .action("CHANGE_PERMISSION")
                .permission("EDIT")
                .build();

        when(fsNodeRepository.findById(nodeId)).thenReturn(Optional.of(testNode));
        when(fsShareRepository.findByNodeId(nodeId)).thenReturn(Optional.of(share));
        when(fsNodePermissionRepository.findByCodeString("EDIT")).thenReturn(Optional.of(editPermission));
        when(fsNodeShareUserRepository.findByNodeId(nodeId)).thenReturn(List.of(shareUser));

        NodeShareDetailResponse response = fsNodeShareService.updateUserGroupShare(ownerId, nodeId, request);

        assertNotNull(response);
        assertEquals("EDIT", response.getPermission());
        verify(fsNodeShareUserRepository).save(argThat(u -> u.getPermission().equals(editPermission)));
    }
}
