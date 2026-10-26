package vn.edu.hust.kat.storageservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import vn.edu.hust.kat.storageservice.dto.response.NodeResponse;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.service.FsFolderService;
import vn.edu.hust.kat.storageservice.service.FsNodeService;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FsNodeControllerTest {

    private MockMvc mockMvc;

    @Mock
    private FsNodeService fsNodeService;

    @Mock
    private FsFolderService fsFolderService;

    @InjectMocks
    private FsNodeController fsNodeController;

    private UUID userId;
    private FsUser authUser;

    @BeforeEach
    void setUp() {
        userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        authUser = FsUser.builder()
                .id(userId)
                .storagePrefix("users/" + userId + "/")
                .storageBucketName("smart-cloud-storage")
                .build();

        // Custom resolver để inject authUser vào @AuthenticationPrincipal
        HandlerMethodArgumentResolver authPrincipalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                return authUser;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(fsNodeController)
                .setCustomArgumentResolvers(authPrincipalResolver)
                .build();
    }

    @Test
    void softDeleteNode_Success_NodesPath() throws Exception {
        doNothing().when(fsNodeService).softDeleteNode(userId, "3");

        mockMvc.perform(delete("/nodes/3")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đã chuyển vào thùng rác thành công"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist()) // In standard JSON without non-null or null
                .andExpect(jsonPath("$.timestamp").exists());

        verify(fsNodeService, times(1)).softDeleteNode(userId, "3");
    }

    @Test
    void softDeleteNode_Success_StorageServiceNodesPath() throws Exception {
        doNothing().when(fsNodeService).softDeleteNode(userId, "5");

        mockMvc.perform(delete("/storage-service/nodes/5")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đã chuyển vào thùng rác thành công"))
                .andExpect(jsonPath("$.code").value(200));

        verify(fsNodeService, times(1)).softDeleteNode(userId, "5");
    }

    @Test
    void restoreNode_Success_NodesPath() throws Exception {
        NodeResponse response = NodeResponse.builder()
                .id(3L)
                .parentId(2L)
                .name("hop-dong-da-ky.pdf")
                .isFolder(false)
                .nodeStatus("ACTIVE")
                .mimeType("application/pdf")
                .sizeBytes(1048576L)
                .etag("d41d8cd98f00b204e9800998ecf8427e")
                .isStarred(true)
                .createdAt(LocalDateTime.of(2026, 10, 18, 10, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 10, 20, 16, 48, 0))
                .build();

        when(fsNodeService.restoreNode(userId, "3")).thenReturn(response);

        mockMvc.perform(post("/nodes/3/restore")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Khôi phục thành công"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(3L))
                .andExpect(jsonPath("$.data.parent_id").value(2L))
                .andExpect(jsonPath("$.data.name").value("hop-dong-da-ky.pdf"))
                .andExpect(jsonPath("$.data.is_folder").value(false))
                .andExpect(jsonPath("$.data.node_status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.mime_type").value("application/pdf"))
                .andExpect(jsonPath("$.data.size_bytes").value(1048576L))
                .andExpect(jsonPath("$.data.etag").value("d41d8cd98f00b204e9800998ecf8427e"))
                .andExpect(jsonPath("$.data.is_starred").value(true))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(fsNodeService, times(1)).restoreNode(userId, "3");
    }

    @Test
    void restoreNode_Success_StorageServiceNodesPath() throws Exception {
        NodeResponse response = NodeResponse.builder()
                .id(7L)
                .parentId(1L)
                .name("documents")
                .isFolder(true)
                .nodeStatus("ACTIVE")
                .isStarred(false)
                .build();

        when(fsNodeService.restoreNode(userId, "7")).thenReturn(response);

        mockMvc.perform(post("/storage-service/nodes/7/restore")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Khôi phục thành công"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(7L))
                .andExpect(jsonPath("$.data.name").value("documents"))
                .andExpect(jsonPath("$.data.is_folder").value(true));

        verify(fsNodeService, times(1)).restoreNode(userId, "7");
    }
}
