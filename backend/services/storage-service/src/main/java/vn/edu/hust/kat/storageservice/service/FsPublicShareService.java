package vn.edu.hust.kat.storageservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.common.api.exception.ResourceNotFoundException;
import vn.edu.hust.kat.storageservice.config.MinioProperties;
import vn.edu.hust.kat.storageservice.dto.request.CreateFolderRequest;
import vn.edu.hust.kat.storageservice.dto.request.DownloadUrlRequest;
import vn.edu.hust.kat.storageservice.dto.request.FileDownloadItemRequest;
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
import vn.edu.hust.kat.storageservice.utils.Constants;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FsPublicShareService {

    private final FsNodeService fsNodeService;
    private final FsNodeFolderService fsNodeFolderService;
    private final FileUploadService fileUploadService;
    private final FileDownloadService fileDownloadService;
    private final FsShareRepository fsShareRepository;
    private final FsNodeRepository fsNodeRepository;
    private final MinioProperties minioProperties;
    private final PasswordEncoder passwordEncoder;
    private final PublicShareTokenService publicShareTokenService;

    /**
     * Xem thông tin cơ bản của liên kết chia sẻ công khai
     */
    @Transactional(readOnly = true)
    public PublicShareInfoResponse getPublicShareInfo(String shareToken) {
        FsShare share = getShareAndValidatePublic(shareToken);
        FsNode node = validateShareNode(share);

        String permCode = (share.getPermission() != null && share.getPermission().getCode() != null)
                ? share.getPermission().getCode().name()
                : "VIEW";

        String ownerName = null;
        if (node.getUser() != null && node.getUser().getStoragePrefix() != null) {
            ownerName = node.getUser().getStoragePrefix().replaceFirst("^users/", "").replaceFirst("/$", "");
        }

        return PublicShareInfoResponse.builder()
                .shareToken(share.getShareToken())
                .nodeName(node.getName())
                .isFolder(node.getIsFolder())
                .mimeType(node.getMimeType())
                .sizeBytes(node.getSizeBytes() != null ? node.getSizeBytes() : 0L)
                .permission(permCode)
                .requiresPassword(Boolean.TRUE.equals(share.getIsPasswordProtected()))
                .expiresAt(share.getExpiresAt())
                .ownerDisplayName(ownerName)
                .build();
    }

    /**
     * Mở khóa Truy cập Link Công khai
     */
    @Transactional
    public PublicAccessSessionResponse accessPublicShare(String shareToken, PublicAccessRequest request) {
        FsShare share = getShareAndValidatePublic(shareToken);
        FsNode node = validateShareNode(share);

        // Kiểm tra mật khẩu nếu link có đặt mật khẩu
        if (Boolean.TRUE.equals(share.getIsPasswordProtected())) {
            String password = (request != null && request.getPassword() != null) ? request.getPassword().trim() : "";
            if (password.isEmpty()) {
                throw new PasswordRequiredException("Liên kết chia sẻ này yêu cầu mật khẩu truy cập");
            }
            if (share.getPasswordHash() == null || !passwordEncoder.matches(password, share.getPasswordHash())) {
                throw new InvalidSharePasswordException("Mật khẩu truy cập không chính xác");
            }
        }

        // Tăng số lượt truy cập thực tế
        share.setAccessCount((share.getAccessCount() != null ? share.getAccessCount() : 0) + 1);
        fsShareRepository.save(share);

        // Tính thời gian hiệu lực cho phiên (mặc định 7200 giây hoặc theo thời gian còn lại của link)
        long expiresInSeconds = 7200L;
        if (share.getExpiresAt() != null) {
            long remainingSeconds = Duration.between(LocalDateTime.now(), share.getExpiresAt()).getSeconds();
            if (remainingSeconds <= 0) {
                share.setStatus(FsShare.ShareStatus.EXPIRED);
                fsShareRepository.save(share);
                throw new ShareExpiredException("Liên kết chia sẻ đã hết hạn sử dụng");
            }
            expiresInSeconds = Math.min(expiresInSeconds, remainingSeconds);
        }

        String permission = (share.getPermission() != null && share.getPermission().getCode() != null)
                ? share.getPermission().getCode().name()
                : "VIEW";

        String publicAccessToken = publicShareTokenService.generatePublicAccessToken(
                shareToken, node.getId(), permission, expiresInSeconds
        );

        return PublicAccessSessionResponse.builder()
                .publicAccessToken(publicAccessToken)
                .tokenType("Bearer")
                .expiresInSeconds(expiresInSeconds)
                .permission(permission)
                .node(PublicShareNodeInfo.from(node))
                .build();
    }

    /**
     * Duyệt Thư mục Con Công khai
     */
    @Transactional(readOnly = true)
    public PagedResponse<NodeResponse> getChildNodes(
            String shareToken,
            String shareAccessToken,
            String parentIdStr,
            String nodeType,
            String name,
            String mimeType,
            int page,
            int size,
            List<String> sorts
    ) {
        FsShare share = validateAndGetActiveShare(shareToken, shareAccessToken);
        FsNode sharedNode = share.getNode();


        if (!sharedNode.getIsFolder()) {
            throw new BadRequestException("Đối tượng được chia sẻ là một tệp tin, không phải thư mục");
        }

        Boolean isFolder = fsNodeService.parseNodeType(nodeType);
        String trimmedName = (name != null && !name.isBlank()) ? name.trim() : null;
        String trimmedMimeType = (mimeType != null && !mimeType.isBlank()) ? mimeType.trim() : null;

        int pageNumber = Math.max(0, page);
        int pageSize = size > 0 ? size : 20;
        Pageable pageable = PageRequest.of(pageNumber, pageSize, fsNodeService.parseSort(sorts));

        FsNode targetFolder;
        if (isRootFromSharedFolderIdentifier(parentIdStr)) {
            targetFolder = sharedNode;
        } else {
            try {
                Long parentId = Long.parseLong(parentIdStr);
                targetFolder = fsNodeRepository.findById(parentId)
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thư mục con hoặc đã bị xóa"));

                if (!fsNodeRepository.isDescendant(targetFolder.getId(), sharedNode.getId())) {
                    throw new AccessDeniedException("Thư mục yêu cầu không thuộc phạm vi thư mục được chia sẻ");
                }

                if (!Boolean.TRUE.equals(targetFolder.getIsFolder()) || targetFolder.getNodeStatus() != FsNode.Status.ACTIVE || targetFolder.getDeletedAt() != null) {
                    throw new BadRequestException("Không tìm thấy thư mục hoặc đã bị xóa");
                }
            } catch (NumberFormatException e){
                throw new BadRequestException("Node Id không hợp lệ");
            }
        }


        Page<FsNode> childNodes = fsNodeRepository.findChildNodesWithLtree(
                sharedNode.getUser().getId(),
                targetFolder.getMaterializedPath(),
                isFolder,
                trimmedName,
                trimmedMimeType,
                pageable
        );

        return PagedResponse.from(childNodes.map(NodeResponse::from));
    }

    /**
     * Tải Tệp tin Công khai
     */
    @Transactional(readOnly = true)
    public PresignedDownloadResponse downloadFile(
            String shareToken,
            String shareAccessToken,
            PublicDownloadRequest request
    ) {
        FsShare share = validateAndGetActiveShare(shareToken, shareAccessToken);
        FsNode sharedNode = share.getNode();

        Long targetNodeId;
        if (Boolean.TRUE.equals(sharedNode.getIsFolder())) {
            if (request == null || request.getNodeId() == null) {
                throw new BadRequestException("Cần cung cấp node_id của file con cần tải trong thư mục chia sẻ");
            }
            targetNodeId = request.getNodeId();

            FsNode targetFile = fsNodeRepository.findById(targetNodeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy file với ID: " + targetNodeId));

            if (!fsNodeRepository.isDescendant(targetFile.getId(), sharedNode.getId())) {
                throw new AccessDeniedException("File yêu cầu không thuộc phạm vi thư mục được chia sẻ");
            }

            if (Boolean.TRUE.equals(targetFile.getIsFolder())) {
                throw new BadRequestException("node_id được chỉ định là thư mục, không thể tải xuống");
            }
        } else {
            if (request != null && request.getNodeId() != null && !request.getNodeId().equals(sharedNode.getId())) {
                throw new BadRequestException("node_id không khớp với file được chia sẻ");
            }
            targetNodeId = sharedNode.getId();
        }

        FsUser owner = sharedNode.getUser();
        DownloadUrlRequest downloadRequest = DownloadUrlRequest.builder()
                .files(List.of(FileDownloadItemRequest.builder()
                        .nodeId(String.valueOf(targetNodeId))
                        .build()))
                .build();

        DownloadUrlsResponse downloadUrlsResponse = fileDownloadService.getDownloadUrls(owner.getId(), downloadRequest);
        if (downloadUrlsResponse == null || downloadUrlsResponse.getDownloadUrls() == null || downloadUrlsResponse.getDownloadUrls().isEmpty()) {
            throw new ResourceNotFoundException("Không thể tạo liên kết tải xuống cho tệp tin này");
        }
        DownloadUrlItemResponse item = downloadUrlsResponse.getDownloadUrls().getFirst();

        int expirySeconds = minioProperties.getPresignedUrlExpirySeconds();
        return PresignedDownloadResponse.builder()
                .nodeId(targetNodeId)
                .fileName(item.getFileName())
                .downloadUrl(item.getUrl())
                .expiresInSeconds(expirySeconds)
                .build();
    }

    /**
     * Tạo Folder con trong shared folder (chỉ khi có quyền EDIT)
     */
    @Transactional
    public NodeResponse createFolder(
            String shareToken,
            String shareAccessToken,
            CreateFolderRequest request
    ) {
        FsShare share = validateAndGetActiveShare(shareToken, shareAccessToken);
        validateEditPermission(share);

        FsNode sharedNode = share.getNode();

        if (!sharedNode.getIsFolder()) {
            throw new BadRequestException("Liên kết chia sẻ không phải là một thư mục");
        }

        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new BadRequestException("Tên thư mục không được để trống");
        }

        String trimmedName = request.getName().trim();
        String requestParentIdStr = request.getParentId();

        try {
            Long parentId = Long.parseLong(request.getParentId().trim());

            // Kiểm tra đầu vào target node id có thuộc phạm vi của node chia sẻ không
            if (!fsNodeRepository.isDescendant(parentId, sharedNode.getId())) {
                throw new AccessDeniedException("Thư mục cha không thuộc phạm vi thư mục được chia sẻ");
            }

            // Tạo thư mục trên quota của chủ sở hữu thư mục đã chia sẻ
            FsUser owner = sharedNode.getUser();
            return fsNodeFolderService.createFolder(owner.getId(), requestParentIdStr, trimmedName);

        } catch (NumberFormatException e) {
            throw new BadRequestException("parent_id không hợp lệ: " + request.getParentId());
        }
    }

    /**
     * Xin URL Upload Tệp mới vào shared folder (chỉ khi có quyền EDIT)
     */
    @Transactional
    public UploadUrlsResponse getUploadUrls(
            String shareToken,
            String shareAccessToken,
            UploadUrlRequest request
    ) {
        FsShare share = validateAndGetActiveShare(shareToken, shareAccessToken);
        validateEditPermission(share);

        FsNode sharedNode = share.getNode();
        if (!Boolean.TRUE.equals(sharedNode.getIsFolder())) {
            throw new BadRequestException("Liên kết chia sẻ không phải là một thư mục");
        }

        if (request == null || request.getFiles() == null || request.getFiles().isEmpty()) {
            throw new BadRequestException("Danh sách tệp tin yêu cầu tải lên không được để trống");
        }

        // Chuẩn hóa và kiểm tra phạm vi các thư mục đích trong request phải thuộc shared folder
        List<FileUploadItemRequest> normalizedFiles = new ArrayList<>();
        for (FileUploadItemRequest item : request.getFiles()) {
            String parentIdStr = item.getParentId();
            if (parentIdStr == null || parentIdStr.isBlank() || parentIdStr.equals(String.valueOf(sharedNode.getId()))) {
                parentIdStr = String.valueOf(sharedNode.getId());
            } else {
                Long parentId;
                try {
                    parentId = Long.parseLong(parentIdStr.trim());
                } catch (NumberFormatException e) {
                    throw new BadRequestException("parent_id không hợp lệ: " + parentIdStr);
                }

                FsNode parentFolder = fsNodeRepository.findById(parentId)
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thư mục cha với ID: " + parentId));

                if (!parentFolder.getId().equals(sharedNode.getId()) && !fsNodeRepository.isDescendant(parentFolder.getId(), sharedNode.getId())) {
                    throw new AccessDeniedException("Thư mục cha không thuộc phạm vi thư mục được chia sẻ");
                }

                if (!Boolean.TRUE.equals(parentFolder.getIsFolder()) || parentFolder.getNodeStatus() != FsNode.Status.ACTIVE || parentFolder.getDeletedAt() != null) {
                    throw new BadRequestException("Thư mục cha không hợp lệ hoặc đã bị xóa");
                }
            }

            normalizedFiles.add(FileUploadItemRequest.builder()
                    .name(item.getName())
                    .size(item.getSize())
                    .hash(item.getHash())
                    .mimeType(item.getMimeType())
                    .parentId(parentIdStr)
                    .build());
        }

        FsUser owner = sharedNode.getUser();
        UploadUrlRequest normalizedRequest = UploadUrlRequest.builder()
                .files(normalizedFiles)
                .build();

        return fileUploadService.getUploadUrls(owner.getId(), normalizedRequest);
    }

    // =========================================================================
    // Private Helpers
    // =========================================================================

    private FsShare getShareAndValidatePublic(String shareToken) {
        if (shareToken == null || shareToken.isBlank()) {
            throw new ShareNotFoundException("Token chia sẻ không được để trống");
        }

        FsShare share = fsShareRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new ShareNotFoundException("Liên kết chia sẻ không tồn tại hoặc đã bị thu hồi"));

        if (!isPublicMode(share.getShareMode()) || !Boolean.TRUE.equals(share.getIsPublic())) {
            throw new ShareNotFoundException("Liên kết chia sẻ không tồn tại hoặc không ở chế độ công khai");
        }

        if (share.getStatus() == FsShare.ShareStatus.REVOKED) {
            throw new ShareNotFoundException("Liên kết chia sẻ không tồn tại hoặc đã bị thu hồi");
        }

        if (share.getExpiresAt() != null && LocalDateTime.now().isAfter(share.getExpiresAt())) {
            share.setStatus(FsShare.ShareStatus.EXPIRED);
            fsShareRepository.save(share);
            throw new ShareExpiredException("Liên kết chia sẻ đã hết hạn sử dụng");
        }

        if (share.getMaxAccessCount() != null && share.getMaxAccessCount() > 0 &&
                share.getAccessCount() != null && share.getAccessCount() >= share.getMaxAccessCount()) {
            share.setStatus(FsShare.ShareStatus.EXPIRED);
            fsShareRepository.save(share);
            throw new ShareExpiredException("Liên kết chia sẻ đã vượt quá số lượt truy cập cho phép");
        }

        if (share.getStatus() != FsShare.ShareStatus.ACTIVE) {
            throw new ShareExpiredException("Liên kết chia sẻ không còn hoạt động");
        }

        return share;
    }

    private FsNode validateShareNode(FsShare share) {
        FsNode node = share.getNode();
        if (node == null || node.getNodeStatus() == FsNode.Status.DELETED || node.getDeletedAt() != null) {
            throw new ShareNotFoundException("Tệp tin hoặc thư mục được chia sẻ không còn tồn tại");
        }
        return node;
    }


    private FsShare validateAndGetActiveShare(String shareToken, String shareAccessToken) {
        FsShare share = getShareAndValidatePublic(shareToken);
        validateShareNode(share);

        // Nếu link có mật khẩu bảo vệ, bắt buộc phải có token phiên hợp lệ
        if (Boolean.TRUE.equals(share.getIsPasswordProtected())) {
            publicShareTokenService.validateAndExtractClaims(shareAccessToken, shareToken);
        } else if (shareAccessToken != null && !shareAccessToken.isBlank()) {
            // Nếu gửi kèm token dù không bắt buộc, xác thực hợp lệ
            try {
                publicShareTokenService.validateAndExtractClaims(shareAccessToken, shareToken);
            } catch (Exception e) {
                log.debug("Token kèm theo không bắt buộc nhưng không hợp lệ: {}", e.getMessage());
            }
        }

        return share;
    }

    private void validateEditPermission(FsShare share) {
        if (share.getPermission() == null ||
                !FsNodePermission.EDIT.equalsIgnoreCase(share.getPermission().getCode().name())) {
            throw new AccessDeniedException("Bạn không có quyền để thực hiện thao tác này");
        }
    }

    private boolean isRootFromSharedFolderIdentifier(String parentId) {
        return parentId == null
                || parentId.isBlank()
                || Constants.NULL.equalsIgnoreCase(parentId.trim());
    }

    private boolean isPublicMode(FsShare.ShareMode mode) {
        return mode == FsShare.ShareMode.PUBLIC || mode == FsShare.ShareMode.PUBLIC_LINK;
    }
}
