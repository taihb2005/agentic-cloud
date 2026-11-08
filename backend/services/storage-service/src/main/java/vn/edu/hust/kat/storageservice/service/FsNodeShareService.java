package vn.edu.hust.kat.storageservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.common.api.exception.DuplicateResourceException;
import vn.edu.hust.kat.common.api.exception.ResourceNotFoundException;
import vn.edu.hust.kat.storageservice.dto.request.ConfigureShareRequest;
import vn.edu.hust.kat.storageservice.dto.request.UpdateUserGroupShareRequest;
import vn.edu.hust.kat.storageservice.dto.response.NodeShareDetailResponse;
import vn.edu.hust.kat.storageservice.dto.response.SharedUserItemResponse;
import vn.edu.hust.kat.storageservice.entity.FsNode;
import vn.edu.hust.kat.storageservice.entity.FsNodePermission;
import vn.edu.hust.kat.storageservice.entity.FsNodeShareUser;
import vn.edu.hust.kat.storageservice.entity.FsShare;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.repository.FsNodePermissionRepository;
import vn.edu.hust.kat.storageservice.repository.FsNodeRepository;
import vn.edu.hust.kat.storageservice.repository.FsNodeShareUserRepository;
import vn.edu.hust.kat.storageservice.repository.FsShareRepository;
import vn.edu.hust.kat.storageservice.repository.FsUserRepository;
import vn.edu.hust.kat.storageservice.utils.ShareTokenGenerator;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FsNodeShareService {

    public static final String MODE_PRIVATE = "PRIVATE";
    public static final String MODE_USER_GROUP = "USER_GROUP";
    public static final String MODE_PUBLIC_LINK = "PUBLIC_LINK";

    private final FsNodeRepository fsNodeRepository;
    private final FsShareRepository fsShareRepository;
    private final FsNodeShareUserRepository fsNodeShareUserRepository;
    private final FsNodePermissionRepository fsNodePermissionRepository;
    private final FsUserRepository fsUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final ShareTokenGenerator shareTokenGenerator;

    @Value("${app.frontend_host}")
    private String frontendHost;
    @Value("${app.frontend_port}")
    private int frontendPort;

    /**
     * Chỉ cấp đúng 1 quyền (VIEW hoặc EDIT) cho hoặc Nhóm người dùng (USER) hoặc Công khai (PUBLIC).
     * Loại trừ lẫn nhau: không thể cùng tồn tại cả hai chế độ.
     */
    @Transactional
    public NodeShareDetailResponse configureShare(UUID callerUserId, Long nodeId, ConfigureShareRequest request) {
        log.info("Cấu hình chia sẻ cho nodeId={}, callerUserId={}, mode={}, permission={}",
                nodeId, callerUserId, request.getShareMode(), request.getPermission());

        FsNode node = getNodeAndValidateOwner(callerUserId, nodeId);
        FsShare.ShareMode targetMode = parseShareMode(request.getShareMode());
        FsNodePermission permissionEntity = getValidPermission(request.getPermission());

        FsShare existingShare = fsShareRepository.findByNodeId(nodeId).orElse(null);
        boolean overrideExisting = Boolean.TRUE.equals(request.getOverrideExisting());

        if (isGroupMode(targetMode)) {
            return configureUserGroupShare(node, callerUserId, permissionEntity, request, existingShare, overrideExisting);
        } else if (isPublicMode(targetMode)) {
            return configurePublicLinkShare(node, callerUserId, permissionEntity, request, existingShare, overrideExisting);
        } else {
            throw new BadRequestException("Chế độ chia sẻ không hợp lệ. Chỉ chấp nhận USER_GROUP hoặc PUBLIC_LINK");
        }
    }

    /**
     * Lấy thông tin cấu hình chia sẻ hiện tại (Owner only)
     */
    @Transactional(readOnly = true)
    public NodeShareDetailResponse getShareDetail(UUID callerUserId, Long nodeId) {
        FsNode node = getNodeAndValidateOwner(callerUserId, nodeId);
        FsShare share = fsShareRepository.findByNodeId(nodeId).orElse(null);

        if (share == null || isNoneMode(share.getShareMode())) {
            return NodeShareDetailResponse.builder()
                    .nodeId(node.getId())
                    .nodeName(node.getName())
                    .isFolder(node.getIsFolder())
                    .shareMode(MODE_PRIVATE)
                    .permission(null)
                    .isPasswordProtected(false)
                    .users(Collections.emptyList())
                    .build();
        }

        return buildShareDetailResponse(node, share);
    }

    /**
     * Cập nhật nhóm người dùng chia sẻ  - chỉ hoạt động khi ở chế độ USER
     */
    @Transactional
    public NodeShareDetailResponse updateUserGroupShare(UUID callerUserId, Long nodeId, UpdateUserGroupShareRequest request) {
        FsNode node = getNodeAndValidateOwner(callerUserId, nodeId);
        FsShare share = fsShareRepository.findByNodeId(nodeId)
                .orElseThrow(() -> new BadRequestException("Node hiện tại chưa được thiết lập chia sẻ"));

        if (!isGroupMode(share.getShareMode())) {
            throw new BadRequestException("Node hiện tại không ở chế độ chia sẻ theo chế độ nhóm. Không thể cập nhật nhóm");
        }

        String action = (request.getAction() != null) ? request.getAction().trim().toUpperCase() : "";
        List<UUID> userIds = (request.getUserIds() != null) ? request.getUserIds() : Collections.emptyList();

        switch (action) {
            case "ADD" -> {
                if (userIds.isEmpty()) {
                    throw new BadRequestException("Danh sách user_ids không được để trống khi thực hiện action ADD");
                }
                validateNoSelfSharing(callerUserId, userIds);
                FsNodePermission targetPermission = (request.getPermission() != null && !request.getPermission().isBlank())
                        ? getValidPermission(request.getPermission())
                        : share.getPermission();

                for (UUID uid : new LinkedHashSet<>(userIds)) {
                    Optional<FsNodeShareUser> existing = fsNodeShareUserRepository.findByNodeIdAndUserId(nodeId, uid);
                    if (existing.isPresent()) {
                        existing.get().setPermission(targetPermission);
                        fsNodeShareUserRepository.save(existing.get());
                    } else {
                        FsNodeShareUser newUser = FsNodeShareUser.builder()
                                .node(node)
                                .userId(uid)
                                .permission(targetPermission)
                                .grantedBy(callerUserId)
                                .build();
                        fsNodeShareUserRepository.save(newUser);
                    }
                }
            }
            case "REMOVE" -> {
                if (userIds.isEmpty()) {
                    throw new BadRequestException("Danh sách user_ids không được để trống khi thực hiện action REMOVE");
                }
                for (UUID uid : userIds) {
                    fsNodeShareUserRepository.deleteByNodeIdAndUserId(nodeId, uid);
                }
            }
            case "CHANGE_PERMISSION" -> {
                if (request.getPermission() == null || request.getPermission().isBlank()) {
                    throw new BadRequestException("permission không được để trống khi đổi quyền nhóm");
                }
                FsNodePermission newPerm = getValidPermission(request.getPermission());
                share.setPermission(newPerm);
                fsShareRepository.save(share);

                List<FsNodeShareUser> currentUsers = fsNodeShareUserRepository.findByNodeId(nodeId);
                for (FsNodeShareUser u : currentUsers) {
                    u.setPermission(newPerm);
                    fsNodeShareUserRepository.save(u);
                }
            }
            case "REPLACE_ALL" -> {
                if (userIds.isEmpty()) {
                    throw new BadRequestException("Danh sách user_ids không được để trống khi thay thế toàn bộ");
                }
                validateNoSelfSharing(callerUserId, userIds);
                FsNodePermission perm = (request.getPermission() != null && !request.getPermission().isBlank())
                        ? getValidPermission(request.getPermission())
                        : share.getPermission();
                share.setPermission(perm);
                fsShareRepository.save(share);

                fsNodeShareUserRepository.deleteByNodeId(nodeId);
                for (UUID uid : new LinkedHashSet<>(userIds)) {
                    FsNodeShareUser newUser = FsNodeShareUser.builder()
                            .node(node)
                            .userId(uid)
                            .permission(perm)
                            .grantedBy(callerUserId)
                            .build();
                    fsNodeShareUserRepository.save(newUser);
                }
            }
            default -> throw new BadRequestException("Thao tác không hợp lệ. Chỉ chấp nhận: ADD, REMOVE, CHANGE_PERMISSION, REPLACE_ALL");
        }

        return buildShareDetailResponse(node, share);
    }

    /**
     * Thu hồi quyền truy cập của một user cụ thể (Owner only)
     */
    @Transactional
    public void revokeUserShare(UUID callerUserId, Long nodeId, UUID targetUserId) {
        getNodeAndValidateOwner(callerUserId, nodeId);
        log.info("Thu hồi quyền của userId={} trên nodeId={}", targetUserId, nodeId);
        fsNodeShareUserRepository.deleteByNodeIdAndUserId(nodeId, targetUserId);
    }

    /**
     * Hủy toàn bộ chia sẻ trên node, đưa về chế độ riêng tư PRIVATE (Owner only)
     */
    @Transactional
    public void revokeAllShare(UUID callerUserId, Long nodeId) {
        getNodeAndValidateOwner(callerUserId, nodeId);
        log.info("Hủy toàn bộ chia sẻ trên nodeId={}", nodeId);

        fsNodeShareUserRepository.deleteByNodeId(nodeId);

        FsShare share = fsShareRepository.findByNodeId(nodeId).orElse(null);
        if (share != null) {
            share.setShareMode(FsShare.ShareMode.NONE);
            share.setIsPublic(false);
            share.setShareToken(null);
            share.setSharedUrl(null);
            share.setPermission(null);
            share.setPasswordHash(null);
            share.setIsPasswordProtected(false);
            share.setStatus(FsShare.ShareStatus.REVOKED);
            fsShareRepository.save(share);
        }
    }

    // =========================================================================
    // Private Helpers & Validation
    // =========================================================================

    private NodeShareDetailResponse configureUserGroupShare(
            FsNode node,
            UUID callerUserId,
            FsNodePermission permissionEntity,
            ConfigureShareRequest request,
            FsShare existingShare,
            boolean overrideExisting
    ) {
        List<UUID> userIds = request.getUserIds();
        if (userIds == null || userIds.isEmpty()) {
            throw new BadRequestException("Danh sách người dùng không được để trống khi chia sẻ theo nhóm");
        }
        validateNoSelfSharing(callerUserId, userIds);

        // Kiểm tra loại trừ lẫn nhau: Đang ở Public link và không có cờ ghi đè
        if (existingShare != null && (Boolean.TRUE.equals(existingShare.getIsPublic()) || isPublicMode(existingShare.getShareMode())) && !overrideExisting) {
            throw new DuplicateResourceException(
                    "Node đang ở chế độ chia sẻ công khai. Không thể đồng thời chia sẻ cho nhóm người dùng. " +
                    "Hãy thu hồi link công khai trước khi chuyển đổi."
            );
        }

        FsShare share =  FsShare.builder().node(node).createdBy(callerUserId)
                .shareMode(FsShare.ShareMode.USER_GROUP)
                .isPublic(false)
                .shareToken(null)
                .sharedUrl(null)
                .permission(permissionEntity)
                .expiresAt(request.getExpiresAt())
                .status(FsShare.ShareStatus.ACTIVE)
                .build();

        applyPasswordIfProvided(share, request.getPassword());
        FsShare savedShare = fsShareRepository.save(share);

        fsNodeShareUserRepository.deleteByNodeId(node.getId());
        for (UUID uid : new LinkedHashSet<>(userIds)) {
            FsNodeShareUser shareUser = FsNodeShareUser.builder()
                    .node(node)
                    .userId(uid)
                    .permission(permissionEntity)
                    .grantedBy(callerUserId)
                    .build();
            fsNodeShareUserRepository.save(shareUser);
        }

        return buildShareDetailResponse(node, savedShare);
    }

    private NodeShareDetailResponse configurePublicLinkShare(
            FsNode node,
            UUID callerUserId,
            FsNodePermission permissionEntity,
            ConfigureShareRequest request,
            FsShare existingShare,
            boolean overrideExisting
    ) {
        // Kiểm tra loại trừ lẫn nhau
        List<FsNodeShareUser> existingUsers = fsNodeShareUserRepository.findByNodeId(node.getId());
        if (!existingUsers.isEmpty() && !overrideExisting) {
            throw new DuplicateResourceException(
                    "Node đang ở chế độ chia sẻ theo nhóm người dùng. Không thể đồng thời chia sẻ công khai. " +
                    "Hãy thu hồi chế độ chia sẻ nhóm trước khi chuyển đổi."
            );
        }

        if (overrideExisting && !existingUsers.isEmpty()) {
            fsNodeShareUserRepository.deleteByNodeId(node.getId());
        }

        FsShare share = (existingShare != null) ? existingShare : FsShare.builder().node(node).createdBy(callerUserId).build();
        share.setShareMode(FsShare.ShareMode.PUBLIC_LINK);
        share.setIsPublic(true);
        if (share.getShareToken() == null || share.getShareToken().isBlank()) {
            share.setShareToken(generateUniqueToken());
        }

        URI publicShareUrl = UriComponentsBuilder.newInstance()
                .scheme("http")
                .host(frontendHost)
                .port(frontendPort)
                .pathSegment("s", "share")
                .queryParam("token", List.of(share.getShareToken()))
                .build()
                .toUri();

        share.setSharedUrl(publicShareUrl.toString());
        share.setPermission(permissionEntity);
        share.setExpiresAt(request.getExpiresAt());
        share.setMaxAccessCount(request.getMaxAccessCount());
        share.setStatus(FsShare.ShareStatus.ACTIVE);

        applyPasswordIfProvided(share, request.getPassword());
        FsShare savedShare = fsShareRepository.save(share);

        return buildShareDetailResponse(node, savedShare);
    }

    public void applyPassword(FsShare share, String password) {
        applyPasswordIfProvided(share, password);
    }

    public void applyPasswordIfProvided(FsShare share, String password) {
        if (password != null) {
            String trimmed = password.trim();
            if (!trimmed.isEmpty()) {
                if (trimmed.length() < 6 || trimmed.length() > 100) {
                    throw new BadRequestException("Mật khẩu bảo vệ phải có độ dài từ 6 đến 100 ký tự");
                }
                share.setPasswordHash(passwordEncoder.encode(trimmed));
                share.setIsPasswordProtected(true);
            } else {
                share.setPasswordHash(null);
                share.setIsPasswordProtected(false);
            }
        } else if (share.getIsPasswordProtected() == null) {
            share.setIsPasswordProtected(false);
        }
    }

    private NodeShareDetailResponse buildShareDetailResponse(FsNode node, FsShare share) {
        String permCode = (share.getPermission() != null && share.getPermission().getCode() != null)
                ? share.getPermission().getCode().name()
                : null;

        String modeName = (share.getShareMode() != null) ? share.getShareMode().name() : MODE_PRIVATE;
        if (isGroupMode(share.getShareMode())) {
            modeName = MODE_USER_GROUP;
        } else if (isPublicMode(share.getShareMode())) {
            modeName = MODE_PUBLIC_LINK;
        }

        List<SharedUserItemResponse> users = Collections.emptyList();
        if (isGroupMode(share.getShareMode())) {
            List<FsNodeShareUser> shareUsers = fsNodeShareUserRepository.findByNodeId(node.getId());
            users = shareUsers.stream().map(u -> {
                String username = null;
                Optional<FsUser> fsUserOpt = fsUserRepository.findById(u.getUserId());
                if (fsUserOpt.isPresent() && fsUserOpt.get().getStoragePrefix() != null) {
                    username = fsUserOpt.get().getStoragePrefix().replaceFirst("^users/", "");
                }
                return SharedUserItemResponse.builder()
                        .userId(u.getUserId())
                        .username(username)
                        .permission(u.getPermission() != null ? u.getPermission().getCode().name() : permCode)
                        .createdAt(u.getCreatedAt())
                        .build();
            }).collect(Collectors.toList());
        }

        return NodeShareDetailResponse.builder()
                .nodeId(node.getId())
                .nodeName(node.getName())
                .isFolder(node.getIsFolder())
                .shareMode(modeName)
                .permission(permCode)
                .isPasswordProtected(Boolean.TRUE.equals(share.getIsPasswordProtected()))
                .sharedUrl(share.getSharedUrl())
                .shareToken(share.getShareToken())
                .expiresAt(share.getExpiresAt())
                .maxAccessCount(share.getMaxAccessCount())
                .accessCount(share.getAccessCount())
                .status(share.getStatus() != null ? share.getStatus().name() : null)
                .users(users)
                .createdAt(share.getCreatedAt())
                .updatedAt(share.getUpdatedAt())
                .build();
    }

    private String generateUniqueToken() {
        int maxRetries = 5;
        for (int i = 0; i < maxRetries; i++) {
            String token = shareTokenGenerator.generateToken();
            if (!fsShareRepository.existsByShareToken(token)) {
                return token;
            }
        }
        throw new IllegalStateException("Không thể sinh token chia sẻ " + maxRetries + " lần thử.");
    }

    public FsNode getNodeAndValidateOwner(UUID callerUserId, Long nodeId) {
        if (callerUserId == null) {
            throw new AccessDeniedException("Yêu cầu xác thực danh tính người dùng");
        }
        FsNode node = fsNodeRepository.findById(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy file hoặc thư mục hoặc đã bị xóa"));

        if (node.isDeleted()) {
            throw new ResourceNotFoundException("Không tìm thấy file hoặc thư mục hoặc đã bị xóa");
        }

        if (node.getUser() == null || !callerUserId.equals(node.getUser().getId())) {
            throw new AccessDeniedException("Chỉ chủ sở hữu mới có quyền thực hiện thao tác chia sẻ trên tệp/thư mục này");
        }
        return node;
    }

    public FsShare.ShareMode parseShareMode(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            throw new BadRequestException("share_mode không được để trống");
        }

        try {
            return FsShare.ShareMode.valueOf(rawMode);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("share_mode không hợp lệ (" + rawMode + ").");
        }
    }

    public boolean isGroupMode(FsShare.ShareMode mode) {
        return mode == FsShare.ShareMode.GROUP || mode == FsShare.ShareMode.USER_GROUP;
    }

    public boolean isPublicMode(FsShare.ShareMode mode) {
        return mode == FsShare.ShareMode.PUBLIC || mode == FsShare.ShareMode.PUBLIC_LINK;
    }

    public boolean isNoneMode(FsShare.ShareMode mode) {
        return mode == null || mode == FsShare.ShareMode.NONE;
    }

    public FsNodePermission getValidPermission(String permission) {
        if (permission == null || permission.isBlank()) {
            throw new BadRequestException("permission không được để trống.");
        }
        return fsNodePermissionRepository.findByCodeString(permission)
                .orElseThrow(() -> new BadRequestException("Mã quyền không hợp lệ (" + permission + ")."));
    }

    public void validateNoSelfSharing(UUID callerUserId, List<UUID> userIds) {
        if (userIds.contains(callerUserId)) {
            throw new BadRequestException("Không thể chia sẻ tệp hoặc thư mục cho chính mình");
        }
    }
}
