package vn.edu.hust.kat.storageservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import vn.edu.hust.kat.storageservice.dto.request.ConfigureShareRequest;
import vn.edu.hust.kat.storageservice.dto.request.UpdateUserGroupShareRequest;
import vn.edu.hust.kat.storageservice.dto.response.NodeShareDetailResponse;
import vn.edu.hust.kat.storageservice.dto.response.SharedUserItemResponse;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.service.FsNodeShareService;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FsNodeShareControllerTest {

    private MockMvc mockMvc;

    @Mock
    private FsNodeShareService fsNodeShareService;

    @InjectMocks
    private FsNodeShareController fsNodeShareController;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UUID ownerId;
    private Long nodeId;
    private FsUser mockAuthUser;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        nodeId = 10L;
        mockAuthUser = FsUser.builder().id(ownerId).storagePrefix("users/owner").build();

        // Custom argument resolver to inject @AuthenticationPrincipal FsUser
        HandlerMethodArgumentResolver authUserResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().isAssignableFrom(FsUser.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return mockAuthUser;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(fsNodeShareController)
                .setCustomArgumentResolvers(authUserResolver)
                .build();
    }

    @Test
    @DisplayName("POST /storage-service/nodes/{id}/share - Cấu hình chia sẻ thành công")
    void testCreateOrUpdateShare() throws Exception {
        ConfigureShareRequest request = ConfigureShareRequest.builder()
                .shareMode("USER_GROUP")
                .permission("VIEW")
                .userIds(List.of(UUID.randomUUID()))
                .build();

        NodeShareDetailResponse response = NodeShareDetailResponse.builder()
                .nodeId(nodeId)
                .nodeName("Tai-lieu-test")
                .isFolder(true)
                .shareMode("USER_GROUP")
                .permission("VIEW")
                .isPasswordProtected(false)
                .users(List.of(SharedUserItemResponse.builder()
                        .userId(UUID.randomUUID())
                        .username("bob")
                        .permission("VIEW")
                        .build()))
                .build();

        when(fsNodeShareService.configureShare(eq(ownerId), eq(nodeId), any(ConfigureShareRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/storage-service/nodes/{id}/share", nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.node_id").value(nodeId))
                .andExpect(jsonPath("$.data.share_mode").value("USER_GROUP"))
                .andExpect(jsonPath("$.data.permission").value("VIEW"));
    }

    @Test
    @DisplayName("GET /storage-service/nodes/{id}/share - Lấy thông tin cấu hình chia sẻ")
    void testGetShareDetail() throws Exception {
        NodeShareDetailResponse response = NodeShareDetailResponse.builder()
                .nodeId(nodeId)
                .nodeName("Tai-lieu-test")
                .isFolder(true)
                .shareMode("USER_GROUP")
                .permission("VIEW")
                .isPasswordProtected(false)
                .build();

        when(fsNodeShareService.getShareDetail(ownerId, nodeId)).thenReturn(response);

        mockMvc.perform(get("/storage-service/nodes/{id}/share", nodeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.node_id").value(nodeId))
                .andExpect(jsonPath("$.data.share_mode").value("USER_GROUP"));
    }

    @Test
    @DisplayName("PUT /storage-service/nodes/{id}/share/users - Cập nhật nhóm chia sẻ")
    void testUpdateUserGroupShare() throws Exception {
        UpdateUserGroupShareRequest request = UpdateUserGroupShareRequest.builder()
                .action("ADD")
                .userIds(List.of(UUID.randomUUID()))
                .build();

        NodeShareDetailResponse response = NodeShareDetailResponse.builder()
                .nodeId(nodeId)
                .shareMode("USER_GROUP")
                .permission("VIEW")
                .build();

        when(fsNodeShareService.updateUserGroupShare(eq(ownerId), eq(nodeId), any(UpdateUserGroupShareRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/storage-service/nodes/{id}/share/users", nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.node_id").value(nodeId));
    }

    @Test
    @DisplayName("DELETE /storage-service/nodes/{id}/share/users/{userId} - Thu hồi quyền một user")
    void testRevokeUserShare() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        doNothing().when(fsNodeShareService).revokeUserShare(ownerId, nodeId, targetUserId);

        mockMvc.perform(delete("/storage-service/nodes/{id}/share/users/{userId}", nodeId, targetUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /storage-service/nodes/{id}/share - Hủy toàn bộ chia sẻ trên node")
    void testRevokeAllShare() throws Exception {
        doNothing().when(fsNodeShareService).revokeAllShare(ownerId, nodeId);

        mockMvc.perform(delete("/storage-service/nodes/{id}/share", nodeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
