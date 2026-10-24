package vn.edu.hust.kat.storageservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edu.hust.kat.storageservice.dto.response.UserQuotaResponse;
import vn.edu.hust.kat.storageservice.service.QuotaService;
import vn.edu.hust.kat.storageservice.service.StorageUserService;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StorageUserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private StorageUserService storageUserService;

    @Mock
    private QuotaService quotaService;

    @InjectMocks
    private StorageUserController storageUserController;

    private UUID userId;
    private UserQuotaResponse quotaResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(storageUserController).build();

        userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        quotaResponse = UserQuotaResponse.builder()
                .userId(userId)
                .storageId(userId)
                .storagePrefix("users/" + userId + "/")
                .storageBucketName("smart-cloud-storage")
                .storageMaxQuotaBytes("16106127360")
                .storageCurrentQuotaBytes("5368709120")
                .rootNodeId("1")
                .rootNodeName("root")
                .rootNodePath("root")
                .currentQuota(5368709120L)
                .maxQuota(16106127360L)
                .unit("MB")
                .usagePercentage(33.33)
                .status("NORMAL")
                .build();
    }

    @Test
    void getUserQuota_Success_PathStorageUsersQuota() throws Exception {
        when(quotaService.getUserQuota(userId)).thenReturn(quotaResponse);

        mockMvc.perform(get("/storage/users/quota")
                        .header("X-User-Id", userId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lấy thông tin dung lượng thành công"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.user_id").value(userId.toString()))
                .andExpect(jsonPath("$.data.storage_id").value(userId.toString()))
                .andExpect(jsonPath("$.data.storage_prefix").value("users/" + userId + "/"))
                .andExpect(jsonPath("$.data.storage_bucket_name").value("smart-cloud-storage"))
                .andExpect(jsonPath("$.data.storage_max_quota_bytes").value("16106127360"))
                .andExpect(jsonPath("$.data.storage_current_quota_bytes").value("5368709120"))
                .andExpect(jsonPath("$.data.root_node_id").value("1"))
                .andExpect(jsonPath("$.data.root_node_name").value("root"))
                .andExpect(jsonPath("$.data.root_node_path").value("root"))
                .andExpect(jsonPath("$.data.current_quota").value(5368709120L))
                .andExpect(jsonPath("$.data.max_quota").value(16106127360L))
                .andExpect(jsonPath("$.data.unit").value("MB"))
                .andExpect(jsonPath("$.data.usage_percetage").value(33.33))
                .andExpect(jsonPath("$.data.status").value("NORMAL"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void getUserQuota_Success_PathStorageServiceStorageUsersQuota() throws Exception {
        when(quotaService.getUserQuota(userId)).thenReturn(quotaResponse);

        mockMvc.perform(get("/storage-service/storage/users/quota")
                        .header("X-User-Id", userId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user_id").value(userId.toString()));
    }

    @Test
    void getUserQuota_MissingUserIdHeader_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/storage/users/quota")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUserQuota_InvalidUserIdHeader_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/storage/users/quota")
                        .header("X-User-Id", "not-a-valid-uuid")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
