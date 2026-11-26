package vn.edu.hust.kat.agentservice.tools;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.gson.Gson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.edu.hust.kat.agentservice.context.UserContextManager;
import vn.edu.hust.kat.agentservice.dto.ActionProposalDto;
import vn.edu.hust.kat.agentservice.dto.ConfirmActionResponse;
import vn.edu.hust.kat.agentservice.entity.AgentAuditLog;
import vn.edu.hust.kat.agentservice.grpc.StorageGrpcClient;
import vn.edu.hust.kat.agentservice.guardrail.GuardrailAspect;
import vn.edu.hust.kat.agentservice.guardrail.snapshot.UndoSnapshotCaptureService;
import vn.edu.hust.kat.agentservice.security.UserPrincipal;
import vn.edu.hust.kat.agentservice.service.ActionConfirmationService;
import vn.edu.hust.kat.agentservice.service.AuditService;
import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.proto.storage.v1.*;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageToolsAndGuardrailTest {

    @Mock
    private StorageGrpcClient storageGrpcClient;

    @Mock
    private AuditService auditService;

    private UserContextManager userContextManager;
    private ActionConfirmationService actionConfirmationService;
    private StorageReadTools readToolsProxy;
    private StorageSafeTools safeToolsProxy;
    private StorageSensitiveTools sensitiveToolsProxy;

    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        bindUserToSecurityContext(currentUserId);

        userContextManager = new UserContextManager();
        actionConfirmationService = new ActionConfirmationService(auditService, null, null);
        UndoSnapshotCaptureService captureService = new UndoSnapshotCaptureService(
                JsonMapper.builder()
                        .findAndAddModules()
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                        .build()
        );
        GuardrailAspect guardrailAspect = new GuardrailAspect(
                userContextManager,
                auditService,
                actionConfirmationService,
                captureService
        );

        readToolsProxy = wrapWithAspect(
                new StorageReadTools(userContextManager, storageGrpcClient),
                guardrailAspect
        );
        safeToolsProxy = wrapWithAspect(
                new StorageSafeTools(userContextManager, storageGrpcClient),
                guardrailAspect
        );
        sensitiveToolsProxy = wrapWithAspect(
                new StorageSensitiveTools(userContextManager, storageGrpcClient, actionConfirmationService),
                guardrailAspect
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readTools_ExecuteImmediatelyViaGrpcWithoutConfirmation_AndAuditReadDescription() {
        NodeItemResponse node = NodeItemResponse.newBuilder()
                .setId("101")
                .setParentId("1")
                .setName("hop_dong_2026.pdf")
                .setIsFolder(false)
                .setSizeBytes(2048)
                .setMimeType("application/pdf")
                .setIsStarred(true)
                .setNodeStatus("ACTIVE")
                .build();

        when(storageGrpcClient.searchNodes(currentUserId, null, "FILE", "hop_dong", null, 0, 20, List.of("name,asc")))
                .thenReturn(SearchNodesResponse.newBuilder().addNodes(node).setTotalElements(1).build());

        String searchJson = readToolsProxy.searchFilesByName(null, "FILE", "hop_dong", null, 0, 20, "name,asc");
        assertTrue(searchJson.contains("hop_dong_2026.pdf"));
        assertTrue(searchJson.contains("\"id\":\"101\""));

        verify(auditService).recordToolAction(
                eq(currentUserId),
                eq("READ_SEARCH"),
                eq("searchFilesByName"),
                isNull(),
                contains("Search or browse child nodes in folder"),
                isNull(),
                eq(AgentAuditLog.Status.SUCCESS),
                eq(AgentAuditLog.HitlStatus.NOT_REQUIRED)
        );

        when(storageGrpcClient.readFileContent(currentUserId, "101"))
                .thenReturn(ReadFileContentResponse.newBuilder()
                        .setNodeId("101")
                        .setName("hop_dong_2026.pdf")
                        .setMimeType("application/pdf")
                        .setSizeBytes(2048)
                        .setContent("Điều 1: Giá thuê văn phòng...")
                        .build());

        String contentResult = readToolsProxy.readFileContent("101");
        assertTrue(contentResult.contains("Điều 1: Giá thuê văn phòng..."));

        verify(auditService).recordToolAction(
                eq(currentUserId),
                eq("READ_FILE_CONTENT"),
                eq("readFileContent"),
                eq("101"),
                contains("Read text content of a file"),
                isNull(),
                eq(AgentAuditLog.Status.SUCCESS),
                eq(AgentAuditLog.HitlStatus.NOT_REQUIRED)
        );

        when(storageGrpcClient.getUserQuota(currentUserId))
                .thenReturn(GetUserQuotaResponse.newBuilder()
                        .setUserId(currentUserId.toString())
                        .setCurrentQuotaBytes(536870912L) // 512 MB
                        .setMaxQuotaBytes(16106127360L)  // 15 GB
                        .setUsagePercentage(3.33)
                        .setStatus("NORMAL")
                        .build());

        String quotaResult = readToolsProxy.getUserStorageQuota();
        assertTrue(quotaResult.contains("512.00 MB"));
        assertTrue(quotaResult.contains("15.00 GB"));
        assertTrue(quotaResult.contains("NORMAL"));
    }

    @Test
    void safeTools_ExecuteViaGrpc_AndCapturePreMutationUndoSnapshotWhenSupportUndoIsTrue() {
        when(storageGrpcClient.getNodeMetadata(currentUserId, "55"))
                .thenReturn(NodeItemResponse.newBuilder()
                        .setId("55")
                        .setParentId("1")
                        .setName("old_report.docx")
                        .setIsFolder(false)
                        .setIsStarred(false)
                        .setNodeStatus("ACTIVE")
                        .build());

        when(storageGrpcClient.renameNode(currentUserId, "55", "new_report.docx"))
                .thenReturn(NodeItemResponse.newBuilder()
                        .setId("55")
                        .setParentId("1")
                        .setName("new_report.docx")
                        .setIsFolder(false)
                        .setIsStarred(false)
                        .setNodeStatus("ACTIVE")
                        .build());

        String renameResult = safeToolsProxy.renameNode("55", "new_report.docx");
        assertTrue(renameResult.contains("new_report.docx"));

        ArgumentCaptor<String> undoCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).recordToolAction(
                eq(currentUserId),
                eq("RENAME"),
                eq("renameNode"),
                eq("55"),
                anyString(),
                undoCaptor.capture(),
                eq(AgentAuditLog.Status.SUCCESS),
                eq(AgentAuditLog.HitlStatus.NOT_REQUIRED)
        );

        String undoPayload = undoCaptor.getValue();
        assertNotNull(undoPayload);
        assertTrue(undoPayload.contains("\"previous_name\":\"old_report.docx\""));
        assertTrue(undoPayload.contains("\"support_undo\":true"));
        assertTrue(undoPayload.contains("\"category\":\"NODE\""));
        assertTrue(undoPayload.contains("\"inverse_action_type\":\"RENAME_NODE\""));
    }

    @Test
    void sensitiveTools_Return403WhenMissingOrInvalidConfirmationToken_AndSucceedOnlyAfterUserApproves() {
        // 1. Direct call without confirmationToken -> 403 FORBIDDEN, gRPC NOT called
        String blockedResult = sensitiveToolsProxy.permanentDelete("99", "");
        assertTrue(blockedResult.contains("\"code\":403"));
        assertTrue(blockedResult.contains("\"status\":\"FORBIDDEN\""));
        verify(storageGrpcClient, never()).permanentDeleteNode(any(), anyString());
        verify(auditService).recordToolAction(
                eq(currentUserId),
                eq("HARD_DELETE"),
                eq("permanentDelete"),
                eq("99"),
                anyString(),
                isNull(),
                eq(AgentAuditLog.Status.BLOCKED),
                eq(AgentAuditLog.HitlStatus.REQUIRED),
                argThat(reason -> reason != null && !reason.isBlank())
        );

        // 2. Agent creates proposal -> status PENDING_CONFIRMATION, returns proposalId (not confirmationToken)
        String proposalJson = sensitiveToolsProxy.createActionProposal(
                "HARD_DELETE",
                "99",
                "Xóa vĩnh viễn tệp tin 99"
        );
        ActionProposalDto proposalDto = new Gson().fromJson(proposalJson, ActionProposalDto.class);
        assertNotNull(proposalDto);
        assertTrue(proposalDto.isRequiresExplicitConfirmation());
        assertNull(proposalDto.getConfirmationToken());
        String proposalId = proposalDto.getProposalId();
        assertNotNull(proposalId);
        verify(auditService).recordToolAction(
                eq(currentUserId),
                eq("HARD_DELETE"),
                eq("createActionProposal"),
                eq("99"),
                anyString(),
                isNull(),
                eq(AgentAuditLog.Status.PENDING_CONFIRMATION),
                eq(AgentAuditLog.HitlStatus.PENDING)
        );

        // 3. Attempting to pass unapproved proposalId as confirmationToken -> still 403 FORBIDDEN!
        String unapprovedAttempt = sensitiveToolsProxy.permanentDelete("99", proposalId);
        assertTrue(unapprovedAttempt.contains("\"code\":403"));
        assertTrue(unapprovedAttempt.contains("PENDING_CONFIRMATION"));
        verify(storageGrpcClient, never()).permanentDeleteNode(any(), anyString());

        // 4. Another user cannot approve this user's proposal
        UUID otherUser = UUID.randomUUID();
        assertThrows(AccessDeniedException.class, () ->
                actionConfirmationService.confirmProposal(otherUser, proposalId, true)
        );

        // 5. Real user approves the proposal -> generates confirmationToken
        ConfirmActionResponse confirmResponse = actionConfirmationService.confirmProposal(currentUserId, proposalId, true);
        assertTrue(confirmResponse.isApproved());
        assertEquals("APPROVED", confirmResponse.getStatus());
        String confirmationToken = confirmResponse.getConfirmationToken();
        assertNotNull(confirmationToken);
        assertNotEquals(proposalId, confirmationToken);
        verify(auditService).recordToolAction(
                eq(currentUserId),
                eq("HARD_DELETE"),
                eq("confirmAction"),
                eq("99"),
                anyString(),
                isNull(),
                eq(AgentAuditLog.Status.SUCCESS),
                eq(AgentAuditLog.HitlStatus.APPROVED)
        );

        // 6. Calling a DIFFERENT tool or DIFFERENT nodeId with this token -> 403 FORBIDDEN
        String wrongNodeAttempt = sensitiveToolsProxy.permanentDelete("100", confirmationToken);
        assertTrue(wrongNodeAttempt.contains("\"code\":403"));
        String wrongActionAttempt = sensitiveToolsProxy.createPublicShare("99", confirmationToken);
        assertTrue(wrongActionAttempt.contains("\"code\":403"));
        verify(storageGrpcClient, never()).permanentDeleteNode(any(), anyString());
        verify(storageGrpcClient, never()).createPublicShare(any(), anyString(), anyString());

        // 7. Calling permanentDelete with the valid confirmationToken -> executes gRPC and succeeds!
        when(storageGrpcClient.permanentDeleteNode(currentUserId, "99"))
                .thenReturn(PermanentDeleteNodeResponse.newBuilder()
                        .setNodeId("99")
                        .setName("secret.txt")
                        .setDeletedCount(1)
                        .setMessage("Đã xóa vĩnh viễn")
                        .build());

        String deleteResult = sensitiveToolsProxy.permanentDelete("99", confirmationToken);
        assertTrue(deleteResult.contains("Thành công"));
        assertTrue(deleteResult.contains("secret.txt"));
        verify(storageGrpcClient, times(1)).permanentDeleteNode(currentUserId, "99");
        verify(auditService).recordToolAction(
                eq(currentUserId),
                eq("HARD_DELETE"),
                eq("permanentDelete"),
                eq("99"),
                anyString(),
                isNull(),
                eq(AgentAuditLog.Status.SUCCESS),
                eq(AgentAuditLog.HitlStatus.APPROVED)
        );

        // 8. Re-using the same confirmationToken a second time -> 403 FORBIDDEN (one-time token)
        String replayAttempt = sensitiveToolsProxy.permanentDelete("99", confirmationToken);
        assertTrue(replayAttempt.contains("\"code\":403"));
        verify(storageGrpcClient, times(1)).permanentDeleteNode(currentUserId, "99");
    }

    @Test
    void auditLogs_RecordRejectedProposalAndFailedToolReasonWithoutConcatenatingErrorToInputPayload() {
        ActionProposalDto proposal = actionConfirmationService.createProposal(
                currentUserId,
                "HARD_DELETE",
                "77",
                "Xóa tệp 77"
        );
        ConfirmActionResponse rejected = actionConfirmationService.confirmProposal(
                currentUserId,
                proposal.getProposalId(),
                false
        );
        assertFalse(rejected.isApproved());
        assertEquals("REJECTED", rejected.getStatus());
        assertThrows(BadRequestException.class, () ->
                actionConfirmationService.confirmProposal(currentUserId, proposal.getProposalId(), true)
        );

        verify(auditService).recordToolAction(
                eq(currentUserId),
                eq("HARD_DELETE"),
                eq("confirmAction"),
                eq("77"),
                anyString(),
                isNull(),
                eq(AgentAuditLog.Status.BLOCKED),
                eq(AgentAuditLog.HitlStatus.REJECTED),
                argThat(reason -> reason != null && !reason.isBlank())
        );

        when(storageGrpcClient.readFileContent(currentUserId, "88"))
                .thenThrow(new RuntimeException("Storage error"));
        assertThrows(RuntimeException.class, () -> readToolsProxy.readFileContent("88"));

        ArgumentCaptor<String> readInputCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).recordToolAction(
                eq(currentUserId),
                eq("READ_FILE_CONTENT"),
                eq("readFileContent"),
                eq("88"),
                readInputCaptor.capture(),
                isNull(),
                eq(AgentAuditLog.Status.FAILED),
                eq(AgentAuditLog.HitlStatus.NOT_REQUIRED),
                eq("Storage error")
        );
        assertFalse(readInputCaptor.getValue().contains("| error="));

        when(storageGrpcClient.renameNode(currentUserId, "55", "broken.txt"))
                .thenThrow(new RuntimeException("Storage error"));
        assertThrows(RuntimeException.class, () -> safeToolsProxy.renameNode("55", "broken.txt"));

        ArgumentCaptor<String> safeInputCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).recordToolAction(
                eq(currentUserId),
                eq("RENAME"),
                eq("renameNode"),
                eq("55"),
                safeInputCaptor.capture(),
                isNull(),
                eq(AgentAuditLog.Status.FAILED),
                eq(AgentAuditLog.HitlStatus.NOT_REQUIRED),
                eq("Storage error")
        );
        assertFalse(safeInputCaptor.getValue().contains("| error="));

        ActionProposalDto failProposal = actionConfirmationService.createProposal(
                currentUserId,
                "HARD_DELETE",
                "90",
                "Xóa tệp 90"
        );
        String token = actionConfirmationService.confirmProposal(currentUserId, failProposal.getProposalId(), true)
                .getConfirmationToken();
        when(storageGrpcClient.permanentDeleteNode(currentUserId, "90"))
                .thenThrow(new RuntimeException("Storage error"));
        assertThrows(RuntimeException.class, () -> sensitiveToolsProxy.permanentDelete("90", token));

        ArgumentCaptor<String> sensitiveInputCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).recordToolAction(
                eq(currentUserId),
                eq("HARD_DELETE"),
                eq("permanentDelete"),
                eq("90"),
                sensitiveInputCaptor.capture(),
                isNull(),
                eq(AgentAuditLog.Status.FAILED),
                eq(AgentAuditLog.HitlStatus.APPROVED),
                eq("Storage error")
        );
        assertFalse(sensitiveInputCaptor.getValue().contains("| error="));
    }

    private void bindUserToSecurityContext(UUID userId) {
        UserPrincipal principal = UserPrincipal.builder()
                .userId(userId)
                .authorities(Collections.emptyList())
                .build();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList())
        );
        SecurityContextHolder.setContext(context);
    }

    private <T> T wrapWithAspect(T target, GuardrailAspect aspect) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(aspect);
        return factory.getProxy();
    }
}
