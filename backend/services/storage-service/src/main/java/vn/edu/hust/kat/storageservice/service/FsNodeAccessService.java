package vn.edu.hust.kat.storageservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.common.api.exception.ResourceNotFoundException;
import vn.edu.hust.kat.storageservice.dto.request.VerifyPasswordRequest;
import vn.edu.hust.kat.storageservice.dto.response.UnlockNodeResponse;
import vn.edu.hust.kat.storageservice.entity.FsNode;
import vn.edu.hust.kat.storageservice.entity.FsNodeShareUser;
import vn.edu.hust.kat.storageservice.exception.InvalidSharePasswordException;
import vn.edu.hust.kat.storageservice.repository.FsNodeRepository;
import vn.edu.hust.kat.storageservice.repository.FsNodeShareUserRepository;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FsNodeAccessService {

    private final FsNodeRepository fsNodeRepository;
    private final FsNodeShareUserRepository fsNodeShareUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final PublicShareTokenService publicShareTokenService;

    /**
     * Mở khóa Node bằng Mật khẩu
     */
    @Transactional(readOnly = true)
    public UnlockNodeResponse verifyNodePassword(UUID userId, Long nodeId, VerifyPasswordRequest request) {
        if (userId == null) {
            throw new AccessDeniedException("Người dùng chưa được xác thực");
        }
        if (nodeId == null) {
            throw new BadRequestException("ID của node không hợp lệ");
        }
        if (request == null || request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BadRequestException("Mật khẩu không được để trống");
        }

        FsNode node = fsNodeRepository.findById(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tệp hoặc thư mục hoặc đã bị xóa"));

        if (node.isDeleted() || node.isDeleted()) {
            throw new ResourceNotFoundException("Tệp hoặc thư mục đã bị xóa");
        }

        boolean isOwner = node.getUser() != null && userId.equals(node.getUser().getId());

        String effectivePermission = null;
        FsNode passwordHolderNode = null;

        if (isOwner) {
            effectivePermission = "OWNER";
            if (node.getShare() != null && Boolean.TRUE.equals(node.getShare().getIsPasswordProtected())) {
                passwordHolderNode = node;
            }
        } else {
            // Check direct share
            Optional<FsNodeShareUser> directShareOpt = fsNodeShareUserRepository.findByNodeIdAndUserId(nodeId, userId);
            if (directShareOpt.isPresent()) {
                effectivePermission = (directShareOpt.get().getPermission() != null && directShareOpt.get().getPermission().getCode() != null)
                        ? directShareOpt.get().getPermission().getCode().name()
                        : "VIEW";
                if (node.getShare() != null && Boolean.TRUE.equals(node.getShare().getIsPasswordProtected())) {
                    passwordHolderNode = node;
                }
            } else {
                // Kiểm tra node tổ tiên xem user có được chia sẻ không
                FsNode ancestor = node.getParent();
                while (ancestor != null) {
                    Optional<FsNodeShareUser> ancestorShareOpt = fsNodeShareUserRepository.findByNodeIdAndUserId(ancestor.getId(), userId);
                    if (ancestorShareOpt.isPresent()) {
                        effectivePermission = (ancestorShareOpt.get().getPermission() != null && ancestorShareOpt.get().getPermission().getCode() != null)
                                ? ancestorShareOpt.get().getPermission().getCode().name()
                                : "VIEW";
                        if (ancestor.getShare() != null && Boolean.TRUE.equals(ancestor.getShare().getIsPasswordProtected())) {
                            passwordHolderNode = ancestor;
                        }
                        break;
                    }
                    ancestor = ancestor.getParent();
                }
            }
        }

        if (effectivePermission == null) {
            throw new AccessDeniedException("User không có quyền thực hiện thao tác này");
        }

        if (node.getShare() != null && Boolean.TRUE.equals(node.getShare().getIsPasswordProtected())) {
            passwordHolderNode = node;
        }

        if (passwordHolderNode == null || passwordHolderNode.getShare().getPasswordHash() == null) {
            throw new BadRequestException("Tệp hoặc thư mục này không được bảo vệ bằng mật khẩu");
        }

        String passwordHash = passwordHolderNode.getShare().getPasswordHash();
        if (!passwordEncoder.matches(request.getPassword(), passwordHash)) {
            throw new InvalidSharePasswordException("Mật khẩu không chính xác");
        }

        Long unlockNodeId = passwordHolderNode.getId();
        long expiresInSeconds = 7200L;
        String shareAccessToken = publicShareTokenService.generateUserAccessToken(
                userId,
                unlockNodeId,
                effectivePermission,
                expiresInSeconds
        );

        return UnlockNodeResponse.builder()
                .nodeId(nodeId)
                .shareAccessToken(shareAccessToken)
                .tokenType("Bearer")
                .expiresInSeconds(expiresInSeconds)
                .effectivePermission(effectivePermission)
                .appliesToDescendants(true)
                .build();
    }
}
