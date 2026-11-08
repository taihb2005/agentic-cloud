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
import vn.edu.hust.kat.common.api.exception.ResourceNotFoundException;
import vn.edu.hust.kat.storageservice.dto.request.VerifyPasswordRequest;
import vn.edu.hust.kat.storageservice.dto.response.UnlockNodeResponse;
import vn.edu.hust.kat.storageservice.entity.FsNode;
import vn.edu.hust.kat.storageservice.entity.FsNodePermission;
import vn.edu.hust.kat.storageservice.entity.FsNodeShareUser;
import vn.edu.hust.kat.storageservice.entity.FsShare;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.exception.InvalidSharePasswordException;
import vn.edu.hust.kat.storageservice.repository.FsNodeRepository;
import vn.edu.hust.kat.storageservice.repository.FsNodeShareUserRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FsNodeAccessServiceTest {

    @Mock
    private FsNodeRepository fsNodeRepository;

    @Mock
    private FsNodeShareUserRepository fsNodeShareUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PublicShareTokenService publicShareTokenService;

    @InjectMocks
    private FsNodeAccessService fsNodeAccessService;

    private UUID ownerId;
    private UUID bobUserId;
    private FsUser ownerUser;
    private FsNode targetNode;
    private FsShare share;
    private FsNodePermission viewPermission;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        bobUserId = UUID.randomUUID();
        ownerUser = FsUser.builder().id(ownerId).storagePrefix("users/owner").build();

        viewPermission = FsNodePermission.builder()
                .id(1L)
                .code(FsNodePermission.PermissionCode.VIEW)
                .build();

        share = FsShare.builder()
                .isPasswordProtected(true)
                .passwordHash("$2a$10$hashedPassword")
                .permission(viewPermission)
                .build();

        targetNode = FsNode.builder()
                .id(5L)
                .name("Tai-lieu-du-an-2026")
                .isFolder(true)
                .user(ownerUser)
                .share(share)
                .build();
    }

    @Test
    @DisplayName("verifyNodePassword - Xác thực mật khẩu thành công với Direct Share")
    void testVerifyNodePassword_DirectShare_Success() {
        FsNodeShareUser shareUser = FsNodeShareUser.builder()
                .node(targetNode)
                .userId(bobUserId)
                .permission(viewPermission)
                .build();

        when(fsNodeRepository.findById(5L)).thenReturn(Optional.of(targetNode));
        when(fsNodeShareUserRepository.findByNodeIdAndUserId(5L, bobUserId)).thenReturn(Optional.of(shareUser));
        when(passwordEncoder.matches("MyPassword123", "$2a$10$hashedPassword")).thenReturn(true);
        when(publicShareTokenService.generateUserAccessToken(eq(bobUserId), eq(5L), eq("VIEW"), eq(7200L)))
                .thenReturn("generated_access_token_jwt");

        VerifyPasswordRequest request = VerifyPasswordRequest.builder()
                .password("MyPassword123")
                .build();

        UnlockNodeResponse response = fsNodeAccessService.verifyNodePassword(bobUserId, 5L, request);

        assertNotNull(response);
        assertEquals(5L, response.getNodeId());
        assertEquals("generated_access_token_jwt", response.getShareAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(7200L, response.getExpiresInSeconds());
        assertEquals("VIEW", response.getEffectivePermission());
        assertTrue(response.getAppliesToDescendants());
    }

    @Test
    @DisplayName("verifyNodePassword - Mật khẩu không chính xác ném InvalidSharePasswordException")
    void testVerifyNodePassword_InvalidPassword() {
        FsNodeShareUser shareUser = FsNodeShareUser.builder()
                .node(targetNode)
                .userId(bobUserId)
                .permission(viewPermission)
                .build();

        when(fsNodeRepository.findById(5L)).thenReturn(Optional.of(targetNode));
        when(fsNodeShareUserRepository.findByNodeIdAndUserId(5L, bobUserId)).thenReturn(Optional.of(shareUser));
        when(passwordEncoder.matches("WrongPassword", "$2a$10$hashedPassword")).thenReturn(false);

        VerifyPasswordRequest request = VerifyPasswordRequest.builder()
                .password("WrongPassword")
                .build();

        assertThrows(InvalidSharePasswordException.class, () ->
                fsNodeAccessService.verifyNodePassword(bobUserId, 5L, request)
        );
    }

    @Test
    @DisplayName("verifyNodePassword - User không có quyền chia sẻ ném AccessDeniedException")
    void testVerifyNodePassword_NotShared_AccessDenied() {
        when(fsNodeRepository.findById(5L)).thenReturn(Optional.of(targetNode));
        when(fsNodeShareUserRepository.findByNodeIdAndUserId(5L, bobUserId)).thenReturn(Optional.empty());

        VerifyPasswordRequest request = VerifyPasswordRequest.builder()
                .password("MyPassword123")
                .build();

        assertThrows(AccessDeniedException.class, () ->
                fsNodeAccessService.verifyNodePassword(bobUserId, 5L, request)
        );
    }

    @Test
    @DisplayName("verifyNodePassword - Node không có mật khẩu ném BadRequestException")
    void testVerifyNodePassword_NotProtected() {
        targetNode.setShare(null);

        FsNodeShareUser shareUser = FsNodeShareUser.builder()
                .node(targetNode)
                .userId(bobUserId)
                .permission(viewPermission)
                .build();

        when(fsNodeRepository.findById(5L)).thenReturn(Optional.of(targetNode));
        when(fsNodeShareUserRepository.findByNodeIdAndUserId(5L, bobUserId)).thenReturn(Optional.of(shareUser));

        VerifyPasswordRequest request = VerifyPasswordRequest.builder()
                .password("MyPassword123")
                .build();

        assertThrows(BadRequestException.class, () ->
                fsNodeAccessService.verifyNodePassword(bobUserId, 5L, request)
        );
    }

    @Test
    @DisplayName("verifyNodePassword - Node không tồn tại ném ResourceNotFoundException")
    void testVerifyNodePassword_NotFound() {
        when(fsNodeRepository.findById(99L)).thenReturn(Optional.empty());

        VerifyPasswordRequest request = VerifyPasswordRequest.builder()
                .password("MyPassword123")
                .build();

        assertThrows(ResourceNotFoundException.class, () ->
                fsNodeAccessService.verifyNodePassword(bobUserId, 99L, request)
        );
    }
}
