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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import vn.edu.hust.kat.common.api.exception.ResourceNotFoundException;
import vn.edu.hust.kat.storageservice.dto.response.EffectiveAccessResponse;
import vn.edu.hust.kat.storageservice.dto.response.NodeOwnerResponse;
import vn.edu.hust.kat.storageservice.dto.response.PagedResponse;
import vn.edu.hust.kat.storageservice.dto.response.SharedWithMeNodeResponse;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.exception.SharedNodesExceptionHandler;
import vn.edu.hust.kat.storageservice.service.SharedNodesService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SharedNodesControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SharedNodesService sharedNodesService;

    @InjectMocks
    private SharedNodesController sharedNodesController;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UUID callerUserId;
    private FsUser mockAuthUser;

    @BeforeEach
    void setUp() {
        callerUserId = UUID.randomUUID();
        mockAuthUser = FsUser.builder().id(callerUserId).storagePrefix("users/bob").build();

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

        mockMvc = MockMvcBuilders.standaloneSetup(sharedNodesController)
                .setCustomArgumentResolvers(authUserResolver)
                .setControllerAdvice(new SharedNodesExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /storage-service/nodes/shared-with-me - Lấy danh sách chia sẻ thành công")
    void testGetSharedWithMe_Success() throws Exception {
        UUID ownerId = UUID.randomUUID();
        NodeOwnerResponse owner = NodeOwnerResponse.builder()
                .userId(ownerId)
                .username("alice")
                .email("alice@example.com")
                .build();

        SharedWithMeNodeResponse item1 = SharedWithMeNodeResponse.builder()
                .nodeId(5L)
                .name("Tai-lieu-du-an-2026")
                .isFolder(true)
                .mimeType(null)
                .sizeBytes(0L)
                .permission("VIEW")
                .isPasswordProtected(true)
                .isUnlocked(false)
                .owner(owner)
                .sharedAt(LocalDateTime.now())
                .build();

        SharedWithMeNodeResponse item2 = SharedWithMeNodeResponse.builder()
                .nodeId(14L)
                .name("Thiet-ke-kien-truc.docx")
                .isFolder(false)
                .mimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .sizeBytes(524288L)
                .permission("EDIT")
                .isPasswordProtected(false)
                .isUnlocked(true)
                .owner(owner)
                .sharedAt(LocalDateTime.now())
                .build();

        PagedResponse<SharedWithMeNodeResponse> pagedResponse = PagedResponse.<SharedWithMeNodeResponse>builder()
                .content(List.of(item1, item2))
                .page(0)
                .size(20)
                .totalElements(2L)
                .totalPages(1)
                .last(true)
                .build();

        when(sharedNodesService.getSharedWithMe(eq(callerUserId), isNull(), isNull(), isNull(), eq(0), eq(20), eq("shared_at,desc"), isNull()))
                .thenReturn(pagedResponse);

        mockMvc.perform(get("/storage-service/nodes/shared-with-me")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Lấy danh sách chia sẻ thành công"))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].node_id").value(5))
                .andExpect(jsonPath("$.data.content[0].name").value("Tai-lieu-du-an-2026"))
                .andExpect(jsonPath("$.data.content[0].is_folder").value(true))
                .andExpect(jsonPath("$.data.content[0].is_password_protected").value(true))
                .andExpect(jsonPath("$.data.content[0].is_unlocked").value(false))
                .andExpect(jsonPath("$.data.content[1].node_id").value(14))
                .andExpect(jsonPath("$.data.content[1].name").value("Thiet-ke-kien-truc.docx"))
                .andExpect(jsonPath("$.data.content[1].is_unlocked").value(true));
    }

    @Test
    @DisplayName("GET /storage-service/nodes/{id}/access - Kiểm tra quyền hiệu dụng thành công")
    void testCheckEffectiveAccess_Success() throws Exception {
        Long targetNodeId = 12L;
        EffectiveAccessResponse response = EffectiveAccessResponse.builder()
                .nodeId(targetNodeId)
                .nodeName("Ban-ve-CAD.dwg")
                .isOwner(false)
                .effectivePermission("VIEW")
                .isInherited(true)
                .inheritedFromNodeId(5L)
                .inheritedFromNodeName("Tai-lieu-du-an-2026")
                .isPasswordProtected(true)
                .isUnlocked(true)
                .canView(true)
                .canEdit(false)
                .build();

        when(sharedNodesService.checkEffectiveAccess(eq(callerUserId), eq(targetNodeId), any()))
                .thenReturn(response);

        mockMvc.perform(get("/storage-service/nodes/{id}/access", targetNodeId)
                        .header("X-Share-Access-Token", "valid_token")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.node_id").value(targetNodeId))
                .andExpect(jsonPath("$.data.node_name").value("Ban-ve-CAD.dwg"))
                .andExpect(jsonPath("$.data.is_owner").value(false))
                .andExpect(jsonPath("$.data.effective_permission").value("VIEW"))
                .andExpect(jsonPath("$.data.is_inherited").value(true))
                .andExpect(jsonPath("$.data.inherited_from_node_id").value(5))
                .andExpect(jsonPath("$.data.inherited_from_node_name").value("Tai-lieu-du-an-2026"))
                .andExpect(jsonPath("$.data.is_password_protected").value(true))
                .andExpect(jsonPath("$.data.is_unlocked").value(true))
                .andExpect(jsonPath("$.data.can_view").value(true))
                .andExpect(jsonPath("$.data.can_edit").value(false));
    }

    @Test
    @DisplayName("GET /storage-service/nodes/{id}/access - Không có quyền trả về 403")
    void testCheckEffectiveAccess_PermissionDenied() throws Exception {
        Long targetNodeId = 99L;
        when(sharedNodesService.checkEffectiveAccess(eq(callerUserId), eq(targetNodeId), any()))
                .thenThrow(new AccessDeniedException("PERMISSION_DENIED: Bạn không có quyền truy cập tệp hoặc thư mục này"));

        mockMvc.perform(get("/storage-service/nodes/{id}/access", targetNodeId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("GET /storage-service/nodes/{id}/access - Node không tồn tại trả về 404")
    void testCheckEffectiveAccess_NotFound() throws Exception {
        Long targetNodeId = 999L;
        when(sharedNodesService.checkEffectiveAccess(eq(callerUserId), eq(targetNodeId), any()))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy tệp hoặc thư mục với ID: " + targetNodeId));

        mockMvc.perform(get("/storage-service/nodes/{id}/access", targetNodeId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }
}
