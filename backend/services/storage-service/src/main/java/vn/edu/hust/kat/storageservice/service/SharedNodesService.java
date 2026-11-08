package vn.edu.hust.kat.storageservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.common.api.exception.ResourceNotFoundException;
import vn.edu.hust.kat.storageservice.dto.response.EffectiveAccessResponse;
import vn.edu.hust.kat.storageservice.dto.response.NodeOwnerResponse;
import vn.edu.hust.kat.storageservice.dto.response.PagedResponse;
import vn.edu.hust.kat.storageservice.dto.response.SharedWithMeNodeResponse;
import vn.edu.hust.kat.storageservice.entity.FsNode;
import vn.edu.hust.kat.storageservice.entity.FsNodePermission;
import vn.edu.hust.kat.storageservice.entity.FsNodeShareUser;
import vn.edu.hust.kat.storageservice.entity.FsShare;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.repository.FsNodeRepository;
import vn.edu.hust.kat.storageservice.repository.FsNodeShareUserRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SharedNodesService {

    private final FsNodeRepository fsNodeRepository;
    private final FsNodeShareUserRepository fsNodeShareUserRepository;
    private final PublicShareTokenService publicShareTokenService;

    /**
     * Danh sách File/Folder Được Chia Sẻ với Tôi (GET /storage-service/nodes/shared-with-me)
     */
    @Transactional(readOnly = true)
    public PagedResponse<SharedWithMeNodeResponse> getSharedWithMe(
            UUID userId,
            String nodeType,
            String permission,
            String name,
            int page,
            int size,
            String sort,
            String shareAccessToken
    ) {
        if (userId == null) {
            throw new AccessDeniedException("Người dùng chưa được xác thực");
        }

        Boolean isFolder = null;
        if (nodeType != null && !nodeType.isBlank()) {
            String upper = nodeType.trim().toUpperCase();
            if ("FOLDER".equals(upper)) {
                isFolder = true;
            } else if ("FILE".equals(upper)) {
                isFolder = false;
            } else {
                throw new BadRequestException("node_type không hợp lệ. Chỉ chấp nhận FILE hoặc FOLDER");
            }
        }

        FsNodePermission.PermissionCode permissionCode = null;
        if (permission != null && !permission.isBlank()) {
            String upper = permission.trim().toUpperCase();
            try {
                permissionCode = FsNodePermission.PermissionCode.valueOf(upper);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("permission không hợp lệ. Chỉ chấp nhận VIEW hoặc EDIT");
            }
        }

        String searchName = (name != null && !name.isBlank()) ? name.trim() : null;

        Sort sortObj = parseSort(sort);
        int pageNumber = Math.max(0, page);
        int pageSize = (size > 0) ? size : 20;
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sortObj);

        Page<FsNodeShareUser> shareUsersPage = fsNodeShareUserRepository.findSharedWithMe(
                userId,
                isFolder,
                permissionCode,
                searchName,
                pageable
        );

        List<SharedWithMeNodeResponse> content = shareUsersPage.getContent().stream().map(su -> {
            FsNode node = su.getNode();
            FsShare share = node.getShare();
            boolean isPasswordProtected = share != null && Boolean.TRUE.equals(share.getIsPasswordProtected());

            boolean isUnlocked = !isPasswordProtected;
            if (isPasswordProtected && shareAccessToken != null && !shareAccessToken.isBlank()) {
                isUnlocked = publicShareTokenService.isNodeUnlockedByUserToken(shareAccessToken, userId, node, node.getId(), fsNodeRepository);
            }

            FsUser ownerUser = node.getUser();
            String ownerUsername = null;
            if (ownerUser != null && ownerUser.getStoragePrefix() != null) {
                ownerUsername = ownerUser.getStoragePrefix().replaceFirst("^users/", "").replaceFirst("/$", "");
            }

            NodeOwnerResponse owner = NodeOwnerResponse.builder()
                    .userId(ownerUser != null ? ownerUser.getId() : null)
                    .username(ownerUsername)
                    .email(null)
                    .build();

            String permStr = (su.getPermission() != null && su.getPermission().getCode() != null)
                    ? su.getPermission().getCode().name()
                    : "VIEW";

            return SharedWithMeNodeResponse.builder()
                    .nodeId(node.getId())
                    .name(node.getName())
                    .isFolder(node.getIsFolder())
                    .mimeType(node.getMimeType())
                    .sizeBytes(node.getSizeBytes() != null ? node.getSizeBytes() : 0L)
                    .permission(permStr)
                    .isPasswordProtected(isPasswordProtected)
                    .isUnlocked(isUnlocked)
                    .owner(owner)
                    .sharedAt(su.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());

        return PagedResponse.<SharedWithMeNodeResponse>builder()
                .content(content)
                .page(shareUsersPage.getNumber())
                .size(shareUsersPage.getSize())
                .totalElements(shareUsersPage.getTotalElements())
                .totalPages(shareUsersPage.getTotalPages())
                .last(shareUsersPage.isLast())
                .build();
    }

    /**
     * Kiểm tra Quyền Hiệu dụng trên một Node (GET /storage-service/nodes/{id}/access)
     */
    @Transactional(readOnly = true)
    public EffectiveAccessResponse checkEffectiveAccess(UUID userId, Long nodeId, String shareAccessToken) {
        if (userId == null) {
            throw new AccessDeniedException("Người dùng chưa được xác thực");
        }
        if (nodeId == null) {
            throw new BadRequestException("ID của node không hợp lệ");
        }

        FsNode node = fsNodeRepository.findById(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tệp hoặc thư mục với ID: " + nodeId));

        if (node.isDeleted() || (node.getNodeStatus() != null && node.getNodeStatus() == FsNode.Status.DELETED)) {
            throw new ResourceNotFoundException("Tệp hoặc thư mục đã bị xóa");
        }

        boolean isOwner = node.getUser() != null && userId.equals(node.getUser().getId());

        if (isOwner) {
            boolean isPasswordProtected = node.getShare() != null && Boolean.TRUE.equals(node.getShare().getIsPasswordProtected());
            return EffectiveAccessResponse.builder()
                    .nodeId(node.getId())
                    .nodeName(node.getName())
                    .isOwner(true)
                    .effectivePermission("OWNER")
                    .isInherited(false)
                    .inheritedFromNodeId(null)
                    .inheritedFromNodeName(null)
                    .isPasswordProtected(isPasswordProtected)
                    .isUnlocked(true)
                    .canView(true)
                    .canEdit(true)
                    .build();
        }

        // 1. Direct share
        Optional<FsNodeShareUser> directShareOpt = fsNodeShareUserRepository.findByNodeIdAndUserId(nodeId, userId);
        if (directShareOpt.isPresent()) {
            FsNodeShareUser directShare = directShareOpt.get();
            String perm = (directShare.getPermission() != null && directShare.getPermission().getCode() != null)
                    ? directShare.getPermission().getCode().name()
                    : "VIEW";

            boolean isPasswordProtected = node.getShare() != null && Boolean.TRUE.equals(node.getShare().getIsPasswordProtected());
            boolean isUnlocked = !isPasswordProtected;
            if (isPasswordProtected && shareAccessToken != null && !shareAccessToken.isBlank()) {
                isUnlocked = publicShareTokenService.isNodeUnlockedByUserToken(shareAccessToken, userId, node, node.getId(), fsNodeRepository);
            }

            boolean canView = isUnlocked && ("VIEW".equals(perm) || "EDIT".equals(perm));
            boolean canEdit = isUnlocked && "EDIT".equals(perm);

            return EffectiveAccessResponse.builder()
                    .nodeId(node.getId())
                    .nodeName(node.getName())
                    .isOwner(false)
                    .effectivePermission(perm)
                    .isInherited(false)
                    .inheritedFromNodeId(null)
                    .inheritedFromNodeName(null)
                    .isPasswordProtected(isPasswordProtected)
                    .isUnlocked(isUnlocked)
                    .canView(canView)
                    .canEdit(canEdit)
                    .build();
        }

        // 2. Inherited share from ancestor
        FsNode ancestor = node.getParent();
        FsNode sharedAncestor = null;
        FsNodeShareUser ancestorShare = null;
        boolean anyPasswordProtected = (node.getShare() != null && Boolean.TRUE.equals(node.getShare().getIsPasswordProtected()));
        Long passwordNodeId = anyPasswordProtected ? node.getId() : null;

        while (ancestor != null) {
            if (ancestor.getShare() != null && Boolean.TRUE.equals(ancestor.getShare().getIsPasswordProtected())) {
                anyPasswordProtected = true;
                if (passwordNodeId == null) {
                    passwordNodeId = ancestor.getId();
                }
            }

            if (sharedAncestor == null) {
                Optional<FsNodeShareUser> shareOpt = fsNodeShareUserRepository.findByNodeIdAndUserId(ancestor.getId(), userId);
                if (shareOpt.isPresent()) {
                    sharedAncestor = ancestor;
                    ancestorShare = shareOpt.get();
                }
            }
            ancestor = ancestor.getParent();
        }

        if (sharedAncestor == null) {
            throw new AccessDeniedException("PERMISSION_DENIED: Bạn không có quyền truy cập tệp hoặc thư mục này");
        }

        String perm = (ancestorShare.getPermission() != null && ancestorShare.getPermission().getCode() != null)
                ? ancestorShare.getPermission().getCode().name()
                : "VIEW";

        boolean isUnlocked = !anyPasswordProtected;
        if (anyPasswordProtected && shareAccessToken != null && !shareAccessToken.isBlank()) {
            isUnlocked = publicShareTokenService.isNodeUnlockedByUserToken(shareAccessToken, userId, node, passwordNodeId, fsNodeRepository);
        }

        boolean canView = isUnlocked && ("VIEW".equals(perm) || "EDIT".equals(perm));
        boolean canEdit = isUnlocked && "EDIT".equals(perm);

        return EffectiveAccessResponse.builder()
                .nodeId(node.getId())
                .nodeName(node.getName())
                .isOwner(false)
                .effectivePermission(perm)
                .isInherited(true)
                .inheritedFromNodeId(sharedAncestor.getId())
                .inheritedFromNodeName(sharedAncestor.getName())
                .isPasswordProtected(anyPasswordProtected)
                .isUnlocked(isUnlocked)
                .canView(canView)
                .canEdit(canEdit)
                .build();
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        Sort.Direction direction = (parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc"))
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        if ("shared_at".equalsIgnoreCase(field) || "sharedAt".equalsIgnoreCase(field) || "created_at".equalsIgnoreCase(field)) {
            field = "createdAt";
        } else if ("name".equalsIgnoreCase(field)) {
            field = "node.name";
        } else if ("size_bytes".equalsIgnoreCase(field) || "sizeBytes".equalsIgnoreCase(field)) {
            field = "node.sizeBytes";
        }
        return Sort.by(direction, field);
    }
}
