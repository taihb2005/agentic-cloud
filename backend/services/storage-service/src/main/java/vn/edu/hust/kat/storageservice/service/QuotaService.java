package vn.edu.hust.kat.storageservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.hust.kat.common.api.exception.ResourceNotFoundException;
import vn.edu.hust.kat.storageservice.config.MinioProperties;
import vn.edu.hust.kat.storageservice.dto.request.InitUserStorageRequest;
import vn.edu.hust.kat.storageservice.dto.response.UserQuotaResponse;
import vn.edu.hust.kat.storageservice.entity.FsNode;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.exception.InsufficientException;
import vn.edu.hust.kat.storageservice.repository.FsUserRepository;
import vn.edu.hust.kat.storageservice.utils.Constants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuotaService {

    private final FsUserRepository fsUserRepository;
    private final MinioProperties minioProperties;

    @Transactional
    public UserQuotaResponse getUserQuota(UUID userId) {
        log.info("Lấy thông tin dung lượng lưu trữ cho userId: {}", userId);

        FsUser fsUser = fsUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với id này. Thử khởi tạo trước khi lấy dữ liệu"));

        long currentQuota = fsUser.getStorageCurrentQuotaBytes() != null
                ? fsUser.getStorageCurrentQuotaBytes()
                : 0L;

        long maxQuota = (fsUser.getStorageMaxQuotaBytes() != null && fsUser.getStorageMaxQuotaBytes() > 0)
                ? fsUser.getStorageMaxQuotaBytes()
                : minioProperties.getDefaultQuotaBytes();

        double usagePercentage = 0.0;
        if (maxQuota > 0) {
            double rawPercentage = ((double) currentQuota / (double) maxQuota) * 100.0;
            usagePercentage = BigDecimal.valueOf(rawPercentage)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        String status = "NORMAL";
        if (currentQuota >= maxQuota) {
            status = "EXCEEDED";
        } else if (usagePercentage >= 90.0) {
            status = "WARNING";
        }

        return UserQuotaResponse.builder()
                .userId(fsUser.getId())
                .storageId(fsUser.getId())
                .currentQuota(currentQuota)
                .maxQuota(maxQuota)
                .unit("MB")
                .usagePercentage(usagePercentage)
                .status(status)
                .build();
    }

    @Transactional
    public void updateUserCurrentQuota(UUID userId, Long currentQuota) {
        fsUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        fsUserRepository.updateCurrentQuota(userId, currentQuota);
    }

    @Transactional
    public void addToUserCurrentQuota(UUID userId, Long willToAddQuota) {
        fsUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        if (isQuotaExceededWithUpcomingNode(userId, willToAddQuota)) {
            throw new InsufficientException("Không đủ dung lượng để thực hiện hành động này");
        }

        int affected = fsUserRepository.addToCurrentQuota(userId, willToAddQuota);
        log.info("Cập nhật dung lượng thành công, có {} dòng ảnh hưởng", affected);
    }

    @Transactional
    public void subtractToUserCurrentQuota(UUID userId, Long willToSubtractQuota) {
        fsUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        if (isQuotaExceededWithUpcomingNode(userId, willToSubtractQuota)) {
            throw new InsufficientException("Không đủ dung lượng để thực hiện hành động này");
        }

        int affected = fsUserRepository.subtractToCurrentQuota(userId, willToSubtractQuota);
        log.info("Cập nhật dung lượng thành công, có {} dòng ảnh hưởng", affected);
    }

    public boolean isQuotaExceededWithUpcomingNode(UUID userId, Long sizeBytes) {
        fsUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));


        return fsUserRepository.isQuotaExceeded(userId, sizeBytes);
    }
}
