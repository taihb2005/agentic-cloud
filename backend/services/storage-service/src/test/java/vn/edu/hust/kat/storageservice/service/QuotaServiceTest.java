package vn.edu.hust.kat.storageservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.hust.kat.common.api.exception.ResourceNotFoundException;
import vn.edu.hust.kat.storageservice.config.MinioProperties;
import vn.edu.hust.kat.storageservice.dto.response.UserQuotaResponse;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.repository.FsUserRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuotaServiceTest {

    @Mock
    private FsUserRepository fsUserRepository;

    @Mock
    private MinioProperties minioProperties;

    @InjectMocks
    private QuotaService quotaService;

    private UUID userId;
    private FsUser fsUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        fsUser = FsUser.builder()
                .id(userId)
                .storagePrefix("users/" + userId + "/")
                .storageBucketName("smart-cloud-storage")
                .storageMaxQuotaBytes(16106127360L) // 15 GB
                .storageCurrentQuotaBytes(5368709120L) // 5 GB (~33.33%)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void getUserQuota_Success_NormalStatus() {
        when(fsUserRepository.findById(userId)).thenReturn(Optional.of(fsUser));

        UserQuotaResponse response = quotaService.getUserQuota(userId);

        assertNotNull(response);
        assertEquals(userId, response.getUserId());
        assertEquals(userId, response.getStorageId());
        assertEquals(5368709120L, response.getCurrentQuota());
        assertEquals(16106127360L, response.getMaxQuota());
        assertEquals("MB", response.getUnit());
        assertEquals(33.33, response.getUsagePercentage());
        assertEquals("NORMAL", response.getStatus());
    }

    @Test
    void getUserQuota_WarningStatus_WhenAbove90Percent() {
        fsUser.setStorageCurrentQuotaBytes(15000000000L); // ~93.13%
        when(fsUserRepository.findById(userId)).thenReturn(Optional.of(fsUser));

        UserQuotaResponse response = quotaService.getUserQuota(userId);

        assertNotNull(response);
        assertEquals("WARNING", response.getStatus());
        assertTrue(response.getUsagePercentage() >= 90.0);
    }

    @Test
    void getUserQuota_ExceededStatus_WhenAtOrAboveLimit() {
        fsUser.setStorageCurrentQuotaBytes(16106127360L); // 100%
        when(fsUserRepository.findById(userId)).thenReturn(Optional.of(fsUser));

        UserQuotaResponse response = quotaService.getUserQuota(userId);

        assertNotNull(response);
        assertEquals("EXCEEDED", response.getStatus());
        assertEquals(100.0, response.getUsagePercentage());
    }

    @Test
    void getUserQuota_ThrowsNotFound_WhenUserNotFound() {
        when(fsUserRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> quotaService.getUserQuota(userId));
    }
}
