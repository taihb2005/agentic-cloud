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
import vn.edu.hust.kat.storageservice.dto.request.VerifyPasswordRequest;
import vn.edu.hust.kat.storageservice.dto.response.UnlockNodeResponse;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.exception.InvalidSharePasswordException;
import vn.edu.hust.kat.storageservice.exception.SharedNodesExceptionHandler;
import vn.edu.hust.kat.storageservice.service.FsNodeAccessService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FsNodeAccessControllerTest {

    private MockMvc mockMvc;

    @Mock
    private FsNodeAccessService fsNodeAccessService;

    @InjectMocks
    private FsNodeAccessController fsNodeAccessController;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UUID callerUserId;
    private Long nodeId;
    private FsUser mockAuthUser;

    @BeforeEach
    void setUp() {
        callerUserId = UUID.randomUUID();
        nodeId = 5L;
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

        mockMvc = MockMvcBuilders.standaloneSetup(fsNodeAccessController)
                .setCustomArgumentResolvers(authUserResolver)
                .setControllerAdvice(new SharedNodesExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /storage-service/nodes/{id}/verify-password - Mở khóa node thành công")
    void testVerifyNodePassword_Success() throws Exception {
        VerifyPasswordRequest request = VerifyPasswordRequest.builder()
                .password("MySuperSecretPassword@2026")
                .build();

        UnlockNodeResponse response = UnlockNodeResponse.builder()
                .nodeId(nodeId)
                .shareAccessToken("mock_jwt_token_for_unlocked_node")
                .tokenType("Bearer")
                .expiresInSeconds(7200L)
                .effectivePermission("VIEW")
                .appliesToDescendants(true)
                .build();

        when(fsNodeAccessService.verifyNodePassword(eq(callerUserId), eq(nodeId), any(VerifyPasswordRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/storage-service/nodes/{id}/verify-password", nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", callerUserId.toString())
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Xác thực mật khẩu thành công. Đã mở khóa quyền truy cập"))
                .andExpect(jsonPath("$.data.node_id").value(nodeId))
                .andExpect(jsonPath("$.data.share_access_token").value("mock_jwt_token_for_unlocked_node"))
                .andExpect(jsonPath("$.data.token_type").value("Bearer"))
                .andExpect(jsonPath("$.data.expires_in_seconds").value(7200))
                .andExpect(jsonPath("$.data.effective_permission").value("VIEW"))
                .andExpect(jsonPath("$.data.applies_to_descendants").value(true));
    }

    @Test
    @DisplayName("POST /storage-service/nodes/{id}/verify-password - Mật khẩu không chính xác trả về 401")
    void testVerifyNodePassword_InvalidPassword() throws Exception {
        VerifyPasswordRequest request = VerifyPasswordRequest.builder()
                .password("WrongPassword123")
                .build();

        when(fsNodeAccessService.verifyNodePassword(eq(callerUserId), eq(nodeId), any(VerifyPasswordRequest.class)))
                .thenThrow(new InvalidSharePasswordException("Mật khẩu không chính xác"));

        mockMvc.perform(post("/storage-service/nodes/{id}/verify-password", nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("Mật khẩu không chính xác"));
    }

    @Test
    @DisplayName("POST /storage-service/nodes/{id}/verify-password - User không có quyền chia sẻ trả về 403")
    void testVerifyNodePassword_PermissionDenied() throws Exception {
        VerifyPasswordRequest request = VerifyPasswordRequest.builder()
                .password("MySuperSecretPassword@2026")
                .build();

        when(fsNodeAccessService.verifyNodePassword(eq(callerUserId), eq(nodeId), any(VerifyPasswordRequest.class)))
                .thenThrow(new AccessDeniedException("PERMISSION_DENIED: User không có quyền chia sẻ trên node này"));

        mockMvc.perform(post("/storage-service/nodes/{id}/verify-password", nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("POST /storage-service/nodes/{id}/verify-password - Node không tồn tại trả về 404")
    void testVerifyNodePassword_NodeNotFound() throws Exception {
        VerifyPasswordRequest request = VerifyPasswordRequest.builder()
                .password("MySuperSecretPassword@2026")
                .build();

        when(fsNodeAccessService.verifyNodePassword(eq(callerUserId), eq(nodeId), any(VerifyPasswordRequest.class)))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy tệp hoặc thư mục với ID: " + nodeId));

        mockMvc.perform(post("/storage-service/nodes/{id}/verify-password", nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }
}
