package vn.edu.hust.kat.storageservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.storageservice.dto.request.SetNodePasswordRequest;
import vn.edu.hust.kat.storageservice.dto.response.NodePasswordStatusResponse;
import vn.edu.hust.kat.storageservice.entity.FsNode;
import vn.edu.hust.kat.storageservice.entity.FsShare;
import vn.edu.hust.kat.storageservice.exception.ShareNotFoundException;
import vn.edu.hust.kat.storageservice.repository.FsShareRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FsNodePasswordService {

    private final FsNodeShareService fsNodeShareService;
    private final FsShareRepository fsShareRepository;

    /**
     * Đặt mật khẩu cho node
     * @param callerUserId userId đang gọi api này
     * @param nodeId nodeId mục tiêu muốn đặt mật khẩu
     */
    @Transactional
    public NodePasswordStatusResponse setNodeSharePassword(
            UUID callerUserId, Long nodeId, SetNodePasswordRequest request
    ) {
        log.info("Thiết lập mật khẩu chia sẻ cho nodeId={}, callerUserId={}", nodeId, callerUserId);

        FsNode node = fsNodeShareService.getNodeAndValidateOwner(callerUserId, nodeId);
        FsShare share = validateShare(node);

        fsNodeShareService.applyPassword(share, request.getPassword());
        FsShare savedShare = fsShareRepository.save(share);

        return NodePasswordStatusResponse.builder()
                .nodeId(node.getId())
                .isPasswordProtected(Boolean.TRUE.equals(savedShare.getIsPasswordProtected()))
                .updatedAt(savedShare.getUpdatedAt())
                .build();
    }

    @Transactional
    public NodePasswordStatusResponse removePassword(UUID callerUserId, Long nodeId) {
        log.info("Xóa mật khẩu chia sẻ cho nodeId={}, callerUserId={}", nodeId, callerUserId);

        FsNode node = fsNodeShareService.getNodeAndValidateOwner(callerUserId, nodeId);
        FsShare share = fsShareRepository.findByNodeId(nodeId)
                .orElseThrow(() -> new BadRequestException("Node hiện tại chưa được thiết lập chia sẻ"));

        share.setPasswordHash(null);
        share.setIsPasswordProtected(Boolean.FALSE);
        FsShare savedShare = fsShareRepository.save(share);

        return NodePasswordStatusResponse.builder()
                .nodeId(node.getId())
                .isPasswordProtected(Boolean.FALSE)
                .updatedAt(savedShare.getUpdatedAt())
                .build();
    }

    private FsShare validateShare(FsNode node) {
        if (node == null) {
            throw new BadRequestException("Node không hợp lệ");
        }

        FsShare share = fsShareRepository.findByNodeId(node.getId())
                .orElse(null);

        if (share == null || node.isDeleted()) {
            throw new ShareNotFoundException("Tệp tin hoặc thư mục không được chia sẻ hoặc không tồn tại");
        }

        return share;
    }
}
