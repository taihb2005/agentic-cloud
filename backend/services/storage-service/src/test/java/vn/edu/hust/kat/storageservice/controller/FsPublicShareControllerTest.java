package vn.edu.hust.kat.storageservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.storageservice.dto.request.CreateFolderRequest;
import vn.edu.hust.kat.storageservice.dto.request.FileUploadItemRequest;
import vn.edu.hust.kat.storageservice.dto.request.PublicAccessRequest;
import vn.edu.hust.kat.storageservice.dto.request.PublicDownloadRequest;
import vn.edu.hust.kat.storageservice.dto.request.UploadUrlRequest;
import vn.edu.hust.kat.storageservice.dto.response.*;
import vn.edu.hust.kat.storageservice.exception.PublicShareExceptionHandler;
import vn.edu.hust.kat.storageservice.service.FsPublicShareService;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FsPublicShareControllerTest {

    private MockMvc mockMvc;

    @Mock
    private FsPublicShareService fsPublicShareService;

    @InjectMocks
    private FsPublicShareController fsPublicShareController;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String shareToken = "pub_tok_123456";
    private final String accessToken = "pub_sess_7f8a9b0c1d2e3f4a";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(fsPublicShareController)
                .setControllerAdvice(new PublicShareExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /storage-service/shares/public/{shareToken} - Xem thông tin link công khai")
    void testGetPublicShareInfo() throws Exception {
        PublicShareInfoResponse response = PublicShareInfoResponse.builder()
                .shareToken(shareToken)
                .nodeName("test-folder")
                .isFolder(true)
                .permission("VIEW")
                .requiresPassword(false)
                .ownerDisplayName("alice")
                .build();

        when(fsPublicShareService.getPublicShareInfo(shareToken)).thenReturn(response);

        mockMvc.perform(get("/storage-service/shares/public/{shareToken}", shareToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.share_token").value(shareToken))
                .andExpect(jsonPath("$.data.node_name").value("test-folder"))
                .andExpect(jsonPath("$.data.is_folder").value(true));
    }

    @Test
    @DisplayName("POST /storage-service/shares/public/{shareToken}/access - Mở khóa truy cập link thành công")
    void testAccessPublicShare() throws Exception {
        PublicAccessRequest request = PublicAccessRequest.builder()
                .password("SecretPass123")
                .build();

        PublicAccessSessionResponse response = PublicAccessSessionResponse.builder()
                .publicAccessToken(accessToken)
                .tokenType("Bearer")
                .expiresInSeconds(7200L)
                .permission("VIEW")
                .node(PublicShareNodeInfo.builder()
                        .id(8L)
                        .name("hop-dong-lao-dong.pdf")
                        .isFolder(false)
                        .sizeBytes(1048576L)
                        .mimeType("application/pdf")
                        .build())
                .build();

        when(fsPublicShareService.accessPublicShare(eq(shareToken), any(PublicAccessRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/storage-service/shares/public/{shareToken}/access", shareToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Truy cập link công khai thành công"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.public_access_token").value(accessToken))
                .andExpect(jsonPath("$.data.node.id").value(8))
                .andExpect(jsonPath("$.data.node.name").value("hop-dong-lao-dong.pdf"));
    }

    @Test
    @DisplayName("GET /storage-service/shares/public/{shareToken}/nodes - Duyệt thư mục con mặc định")
    void testGetChildNodes_DefaultParams_Success() throws Exception {
        PagedResponse<NodeResponse> response = PagedResponse.<NodeResponse>builder()
                .content(List.of(
                        NodeResponse.builder().id(9L).name("file1.pdf").isFolder(false).build(),
                        NodeResponse.builder().id(10L).name("subfolder").isFolder(true).build()
                ))
                .page(0)
                .size(20)
                .totalElements(2L)
                .totalPages(1)
                .last(true)
                .build();

        when(fsPublicShareService.getChildNodes(eq(shareToken), eq(accessToken), isNull(), isNull(), isNull(), isNull(), eq(0), eq(20), any()))
                .thenReturn(response);

        mockMvc.perform(get("/storage-service/shares/public/{shareToken}/nodes", shareToken)
                        .header("X-Share-Access-Token", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total_elements").value(2))
                .andExpect(jsonPath("$.data.content[0].id").value(9))
                .andExpect(jsonPath("$.data.content[1].id").value(10));
    }

    @Test
    @DisplayName("GET /storage-service/shares/public/{shareToken}/nodes - Duyệt thư mục con kèm đầy đủ query params")
    void testGetChildNodes_WithQueryParams_Success() throws Exception {
        PagedResponse<NodeResponse> response = PagedResponse.<NodeResponse>builder()
                .content(List.of(
                        NodeResponse.builder().id(11L).name("filtered-doc.pdf").isFolder(false).mimeType("application/pdf").build()
                ))
                .page(1)
                .size(10)
                .totalElements(1L)
                .totalPages(1)
                .last(true)
                .build();

        when(fsPublicShareService.getChildNodes(eq(shareToken), eq(accessToken), eq("10"), eq("FILE"), eq("filtered"), eq("application/pdf"), eq(1), eq(10), any()))
                .thenReturn(response);

        mockMvc.perform(get("/storage-service/shares/public/{shareToken}/nodes", shareToken)
                        .header("X-Share-Access-Token", accessToken)
                        .param("parent_id", "10")
                        .param("node_type", "FILE")
                        .param("name", "filtered")
                        .param("mime_type", "application/pdf")
                        .param("page", "1")
                        .param("size", "10")
                        .param("sort", "name,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].name").value("filtered-doc.pdf"));
    }

    @Test
    @DisplayName("GET /storage-service/shares/public/{shareToken}/nodes - Thư mục yêu cầu không thuộc phạm vi ném 403")
    void testGetChildNodes_AccessDenied_Returns403() throws Exception {
        when(fsPublicShareService.getChildNodes(eq(shareToken), eq(accessToken), eq("99"), isNull(), isNull(), isNull(), eq(0), eq(20), any()))
                .thenThrow(new AccessDeniedException("Thư mục yêu cầu không thuộc phạm vi thư mục được chia sẻ"));

        mockMvc.perform(get("/storage-service/shares/public/{shareToken}/nodes", shareToken)
                        .header("X-Share-Access-Token", accessToken)
                        .param("parent_id", "99"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("GET /storage-service/shares/public/{shareToken}/nodes - Node chia sẻ là file ném 400")
    void testGetChildNodes_SharedNodeIsFile_Returns400() throws Exception {
        when(fsPublicShareService.getChildNodes(eq(shareToken), any(), any(), any(), any(), any(), anyInt(), anyInt(), any()))
                .thenThrow(new BadRequestException("Đối tượng được chia sẻ là một tệp tin, không phải thư mục"));

        mockMvc.perform(get("/storage-service/shares/public/{shareToken}/nodes", shareToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("POST /storage-service/shares/public/{shareToken}/download - Tải file công khai thành công")
    void testDownloadFile() throws Exception {
        PublicDownloadRequest request = PublicDownloadRequest.builder()
                .nodeId(8L)
                .build();

        PresignedDownloadResponse response = PresignedDownloadResponse.builder()
                .nodeId(8L)
                .fileName("hop-dong-lao-dong.pdf")
                .downloadUrl("http://localhost:9000/download/url")
                .expiresInSeconds(900)
                .build();

        when(fsPublicShareService.downloadFile(eq(shareToken), eq(accessToken), any(PublicDownloadRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/storage-service/shares/public/{shareToken}/download", shareToken)
                        .header("X-Share-Access-Token", accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.node_id").value(8))
                .andExpect(jsonPath("$.data.file_name").value("hop-dong-lao-dong.pdf"))
                .andExpect(jsonPath("$.data.download_url").value("http://localhost:9000/download/url"))
                .andExpect(jsonPath("$.data.expires_in_seconds").value(900));
    }

    @Test
    @DisplayName("POST /storage-service/shares/public/{shareToken}/nodes/folders - Tạo folder con thành công khi có quyền EDIT")
    void testCreateFolder_Success() throws Exception {
        CreateFolderRequest request = CreateFolderRequest.builder()
                .name("Thu-muc-moi")
                .parentId("5")
                .build();

        NodeResponse response = NodeResponse.builder()
                .id(15L)
                .name("Thu-muc-moi")
                .parentId(5L)
                .isFolder(true)
                .build();

        when(fsPublicShareService.createFolder(eq(shareToken), eq(accessToken), any(CreateFolderRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/storage-service/shares/public/{shareToken}/nodes/folders", shareToken)
                        .header("X-Share-Access-Token", accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("Tạo thư mục thành công"))
                .andExpect(jsonPath("$.data.id").value(15))
                .andExpect(jsonPath("$.data.name").value("Thu-muc-moi"));
    }

    @Test
    @DisplayName("POST /storage-service/shares/public/{shareToken}/nodes/folders - Trả về 403 Forbidden nếu link chỉ có quyền VIEW")
    void testCreateFolder_ForbiddenWhenViewOnly() throws Exception {
        CreateFolderRequest request = CreateFolderRequest.builder()
                .name("Thu-muc-moi")
                .parentId("5")
                .build();

        when(fsPublicShareService.createFolder(eq(shareToken), eq(accessToken), any(CreateFolderRequest.class)))
                .thenThrow(new AccessDeniedException("PERMISSION_DENIED: Link chia sẻ chỉ có quyền xem (VIEW)"));

        mockMvc.perform(post("/storage-service/shares/public/{shareToken}/nodes/folders", shareToken)
                        .header("X-Share-Access-Token", accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("POST /storage-service/shares/public/{shareToken}/files/upload-urls - Xin URL upload thành công khi có quyền EDIT")
    void testGetUploadUrls_Success() throws Exception {
        UploadUrlRequest request = UploadUrlRequest.builder()
                .files(List.of(
                        FileUploadItemRequest.builder()
                                .name("file.txt")
                                .size(100L)
                                .hash("abc123hash")
                                .parentId("5")
                                .build()
                ))
                .build();

        UploadUrlsResponse response = UploadUrlsResponse.builder()
                .presignedUrls(List.of(
                        PresignedUrlResponse.builder()
                                .nodeId("20")
                                .url("http://minio/upload/put")
                                .instantUploaded(false)
                                .expiresAt(Instant.now().plusSeconds(900))
                                .build()
                ))
                .build();

        when(fsPublicShareService.getUploadUrls(eq(shareToken), eq(accessToken), any(UploadUrlRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/storage-service/shares/public/{shareToken}/files/upload-urls", shareToken)
                        .header("X-Share-Access-Token", accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.presigned_urls[0].node_id").value("20"));
    }

    @Test
    @DisplayName("POST /storage-service/shares/public/{shareToken}/files/upload-urls - Trả về 403 Forbidden nếu link chỉ có quyền VIEW")
    void testGetUploadUrls_ForbiddenWhenViewOnly() throws Exception {
        UploadUrlRequest request = UploadUrlRequest.builder()
                .files(List.of(
                        FileUploadItemRequest.builder()
                                .name("file.txt")
                                .size(100L)
                                .hash("abc123hash")
                                .parentId("5")
                                .build()
                ))
                .build();

        when(fsPublicShareService.getUploadUrls(eq(shareToken), eq(accessToken), any(UploadUrlRequest.class)))
                .thenThrow(new AccessDeniedException("PERMISSION_DENIED: Link chia sẻ chỉ có quyền xem (VIEW)"));

        mockMvc.perform(post("/storage-service/shares/public/{shareToken}/files/upload-urls", shareToken)
                        .header("X-Share-Access-Token", accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(403));
    }
}
