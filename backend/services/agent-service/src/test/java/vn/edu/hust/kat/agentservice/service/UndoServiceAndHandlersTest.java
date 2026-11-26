package vn.edu.hust.kat.agentservice.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.hust.kat.agentservice.context.UserContextManager;
import vn.edu.hust.kat.agentservice.dto.undo.TurnUndoResponse;
import vn.edu.hust.kat.agentservice.dto.undo.UndoActionResponse;
import vn.edu.hust.kat.agentservice.entity.AgentAuditLog;
import vn.edu.hust.kat.agentservice.entity.ChatConversation;
import vn.edu.hust.kat.agentservice.grpc.StorageGrpcClient;
import vn.edu.hust.kat.agentservice.guardrail.snapshot.UndoSnapshotCaptureService;
import vn.edu.hust.kat.agentservice.guardrail.snapshot.body.InverseActionType;
import vn.edu.hust.kat.agentservice.guardrail.snapshot.body.ShareConfigState;
import vn.edu.hust.kat.agentservice.guardrail.snapshot.payload.MemoryMutationUndoSnapshot;
import vn.edu.hust.kat.agentservice.guardrail.snapshot.payload.NodeMutationUndoSnapshot;
import vn.edu.hust.kat.agentservice.guardrail.snapshot.payload.ShareMutationUndoSnapshot;
import vn.edu.hust.kat.agentservice.guardrail.snapshot.payload.UndoSnapshotPayload;
import vn.edu.hust.kat.agentservice.guardrail.undo.Undoable;
import vn.edu.hust.kat.agentservice.memory.DualLayerMemoryManager;
import vn.edu.hust.kat.agentservice.repository.AgentAuditLogRepository;
import vn.edu.hust.kat.agentservice.tools.StorageSafeTools;
import vn.edu.hust.kat.agentservice.tools.StorageSensitiveTools;
import vn.edu.hust.kat.agentservice.tools.UserMemoryTools;
import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.common.api.exception.ResourceNotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UndoServiceAndHandlersTest {

    @Mock
    private AgentAuditLogRepository auditLogRepository;

    @Mock
    private ChatConversationService chatConversationService;

    @Mock
    private StorageGrpcClient storageGrpcClient;

    @Mock
    private DualLayerMemoryManager memoryManager;

    private JsonMapper jsonMapper;
    private UndoService undoService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jsonMapper = JsonMapper.builder()
                .findAndAddModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .addModule(new JavaTimeModule())
                .build();

        UserContextManager userContextManager = new UserContextManager();
        undoService = new UndoService(
                auditLogRepository,
                chatConversationService,
                jsonMapper,
                List.of(
                        new StorageSafeTools(userContextManager, storageGrpcClient),
                        new StorageSensitiveTools(userContextManager, storageGrpcClient, null),
                        new UserMemoryTools(userContextManager, memoryManager)
                )
        );
    }

    @Test
    void undoAuditLog_NodeRename_RevertsNameAndWritesCompensatingLog() throws Exception {
        UUID logId = UUID.randomUUID();
        NodeMutationUndoSnapshot snap = NodeMutationUndoSnapshot.builder()
                .category(UndoSnapshotPayload.SnapshotCategory.NODE)
                .actionType("RENAME")
                .inverseActionType(InverseActionType.RENAME_NODE)
                .nodeId("42")
                .previousName("old_report.pdf")
                .newName("new_report.pdf")
                .build();

        AgentAuditLog originalLog = AgentAuditLog.builder()
                .id(logId)
                .userId(userId)
                .actionType("RENAME")
                .toolName("renameNode")
                .targetNodeId("42")
                .undoPayload(jsonMapper.writeValueAsString(snap))
                .status(AgentAuditLog.Status.SUCCESS)
                .undoStatus(AgentAuditLog.UndoStatus.UNDOABLE)
                .build();

        when(auditLogRepository.findByIdAndUserId(logId, userId)).thenReturn(Optional.of(originalLog));
        when(auditLogRepository.save(any(AgentAuditLog.class))).thenAnswer(inv -> {
            AgentAuditLog saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });

        UndoActionResponse response = undoService.undoAuditLog(userId, logId);

        assertEquals(AgentAuditLog.UndoStatus.UNDONE.name(), response.getUndoStatus());
        assertEquals(InverseActionType.RENAME_NODE.name(), response.getInverseActionType());
        assertNotNull(response.getCompensatingLogId());
        assertEquals(AgentAuditLog.UndoStatus.UNDONE, originalLog.getUndoStatus());
        assertNotNull(originalLog.getUndoneAt());
        assertNotNull(originalLog.getUndoneByLog());
        verify(storageGrpcClient).renameNode(userId, "42", "old_report.pdf");
    }

    @Test
    void undoAuditLog_PublicShareFromPrivate_CallsRevokeAllShare() throws Exception {
        UUID logId = UUID.randomUUID();
        ShareMutationUndoSnapshot snap = ShareMutationUndoSnapshot.builder()
                .category(UndoSnapshotPayload.SnapshotCategory.SHARE)
                .actionType("PUBLIC_SHARE")
                .inverseActionType(InverseActionType.REVOKE_ALL_SHARE)
                .nodeId("99")
                .previousShareState(ShareConfigState.builder().shareMode("PRIVATE").build())
                .build();

        AgentAuditLog originalLog = AgentAuditLog.builder()
                .id(logId)
                .userId(userId)
                .actionType("PUBLIC_SHARE")
                .toolName("createPublicShare")
                .targetNodeId("99")
                .undoPayload(jsonMapper.writeValueAsString(snap))
                .status(AgentAuditLog.Status.SUCCESS)
                .undoStatus(AgentAuditLog.UndoStatus.UNDOABLE)
                .build();

        when(auditLogRepository.findByIdAndUserId(logId, userId)).thenReturn(Optional.of(originalLog));
        when(auditLogRepository.save(any(AgentAuditLog.class))).thenAnswer(inv -> {
            AgentAuditLog saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });

        UndoActionResponse response = undoService.undoAuditLog(userId, logId);

        assertEquals(AgentAuditLog.UndoStatus.UNDONE.name(), response.getUndoStatus());
        verify(storageGrpcClient).revokeAllShare(userId, "99");
    }

    @Test
    void undoAuditLog_MemoryPreferenceUpdate_RestoresPreviousValue() throws Exception {
        UUID logId = UUID.randomUUID();
        MemoryMutationUndoSnapshot snap = MemoryMutationUndoSnapshot.builder()
                .category(UndoSnapshotPayload.SnapshotCategory.MEMORY)
                .actionType("UPDATE_USER_PREF")
                .inverseActionType(InverseActionType.RESTORE_USER_PREFERENCE)
                .preferenceKey("language")
                .existedBefore(true)
                .previousValue("English")
                .newValue("Vietnamese")
                .build();

        AgentAuditLog originalLog = AgentAuditLog.builder()
                .id(logId)
                .userId(userId)
                .actionType("UPDATE_USER_PREF")
                .toolName("updateUserPreference")
                .undoPayload(jsonMapper.writeValueAsString(snap))
                .status(AgentAuditLog.Status.SUCCESS)
                .undoStatus(AgentAuditLog.UndoStatus.UNDOABLE)
                .build();

        when(auditLogRepository.findByIdAndUserId(logId, userId)).thenReturn(Optional.of(originalLog));
        when(auditLogRepository.save(any(AgentAuditLog.class))).thenAnswer(inv -> {
            AgentAuditLog saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });

        UndoActionResponse response = undoService.undoAuditLog(userId, logId);

        assertEquals(AgentAuditLog.UndoStatus.UNDONE.name(), response.getUndoStatus());
        verify(memoryManager).upsertUserPreference(userId.toString(), "language", "English");
    }

    @Test
    void undoAuditLog_AlreadyUndone_ThrowsBadRequest() {
        UUID logId = UUID.randomUUID();
        AgentAuditLog alreadyUndone = AgentAuditLog.builder()
                .id(logId)
                .userId(userId)
                .status(AgentAuditLog.Status.SUCCESS)
                .undoStatus(AgentAuditLog.UndoStatus.UNDONE)
                .build();
        when(auditLogRepository.findByIdAndUserId(logId, userId)).thenReturn(Optional.of(alreadyUndone));

        assertThrows(BadRequestException.class, () -> undoService.undoAuditLog(userId, logId));
    }

    @Test
    void undoAuditLog_NotOwnedOrMissing_ThrowsNotFound() {
        UUID logId = UUID.randomUUID();
        when(auditLogRepository.findByIdAndUserId(logId, userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> undoService.undoAuditLog(userId, logId));
    }

    @Test
    void undoTurn_ExecutesInStrictLifoOrder_AndRewindsConversation() throws Exception {
        UUID convId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        UUID newLeafId = UUID.randomUUID();

        NodeMutationUndoSnapshot createFolderSnap = NodeMutationUndoSnapshot.builder()
                .category(UndoSnapshotPayload.SnapshotCategory.NODE)
                .actionType("CREATE_FOLDER")
                .inverseActionType(InverseActionType.SOFT_DELETE_CREATED_NODE)
                .nodeId("100")
                .createdNodeId("100")
                .build();

        NodeMutationUndoSnapshot moveSnap = NodeMutationUndoSnapshot.builder()
                .category(UndoSnapshotPayload.SnapshotCategory.NODE)
                .actionType("MOVE")
                .inverseActionType(InverseActionType.MOVE_NODE)
                .nodeId("55")
                .previousParentId("10")
                .newParentId("100")
                .build();

        AgentAuditLog log2Move = AgentAuditLog.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .turnId(turnId)
                .executionOrder(2)
                .actionType("MOVE")
                .toolName("moveNode")
                .targetNodeId("55")
                .undoPayload(jsonMapper.writeValueAsString(moveSnap))
                .status(AgentAuditLog.Status.SUCCESS)
                .undoStatus(AgentAuditLog.UndoStatus.UNDOABLE)
                .build();

        AgentAuditLog log1Create = AgentAuditLog.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .turnId(turnId)
                .executionOrder(1)
                .actionType("CREATE_FOLDER")
                .toolName("createFolder")
                .targetNodeId("100")
                .undoPayload(jsonMapper.writeValueAsString(createFolderSnap))
                .status(AgentAuditLog.Status.SUCCESS)
                .undoStatus(AgentAuditLog.UndoStatus.UNDOABLE)
                .build();

        when(chatConversationService.requireOwnedConversation(userId, convId))
                .thenReturn(ChatConversation.builder().id(convId).userId(userId).build());
        when(auditLogRepository.findByConversationIdAndTurnIdAndUserIdAndUndoStatusOrderByExecutionOrderDesc(
                convId, turnId, userId, AgentAuditLog.UndoStatus.UNDOABLE
        )).thenReturn(List.of(log2Move, log1Create));
        when(auditLogRepository.save(any(AgentAuditLog.class))).thenAnswer(inv -> {
            AgentAuditLog saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
        when(chatConversationService.rewindBeforeTurn(userId, convId, turnId)).thenReturn(newLeafId);

        TurnUndoResponse response = undoService.undoTurn(userId, convId, turnId, true);

        assertEquals(2, response.getTotalActions());
        assertEquals(2, response.getUndoneCount());
        assertEquals(0, response.getFailedCount());
        assertTrue(response.isConversationRewound());
        assertEquals(newLeafId, response.getNewActiveLeafMessageId());

        InOrder inOrder = inOrder(storageGrpcClient);
        inOrder.verify(storageGrpcClient).moveNode(userId, "55", "10");
        inOrder.verify(storageGrpcClient).softDeleteNode(userId, "100");
    }

    @Test
    void undoTurn_FailFastOnError_StopsChainAndSkipsRewind() throws Exception {
        UUID convId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();

        NodeMutationUndoSnapshot moveSnap = NodeMutationUndoSnapshot.builder()
                .category(UndoSnapshotPayload.SnapshotCategory.NODE)
                .actionType("MOVE")
                .inverseActionType(InverseActionType.MOVE_NODE)
                .nodeId("55")
                .previousParentId("10")
                .build();

        NodeMutationUndoSnapshot createSnap = NodeMutationUndoSnapshot.builder()
                .category(UndoSnapshotPayload.SnapshotCategory.NODE)
                .actionType("CREATE_FOLDER")
                .inverseActionType(InverseActionType.SOFT_DELETE_CREATED_NODE)
                .createdNodeId("100")
                .build();

        AgentAuditLog log2Move = AgentAuditLog.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .turnId(turnId)
                .executionOrder(2)
                .actionType("MOVE")
                .toolName("moveNode")
                .undoPayload(jsonMapper.writeValueAsString(moveSnap))
                .status(AgentAuditLog.Status.SUCCESS)
                .undoStatus(AgentAuditLog.UndoStatus.UNDOABLE)
                .build();

        AgentAuditLog log1Create = AgentAuditLog.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .turnId(turnId)
                .executionOrder(1)
                .actionType("CREATE_FOLDER")
                .toolName("createFolder")
                .undoPayload(jsonMapper.writeValueAsString(createSnap))
                .status(AgentAuditLog.Status.SUCCESS)
                .undoStatus(AgentAuditLog.UndoStatus.UNDOABLE)
                .build();

        when(chatConversationService.requireOwnedConversation(userId, convId))
                .thenReturn(ChatConversation.builder().id(convId).userId(userId).build());
        when(auditLogRepository.findByConversationIdAndTurnIdAndUserIdAndUndoStatusOrderByExecutionOrderDesc(
                convId, turnId, userId, AgentAuditLog.UndoStatus.UNDOABLE
        )).thenReturn(List.of(log2Move, log1Create));
        when(storageGrpcClient.moveNode(userId, "55", "10")).thenThrow(new RuntimeException("Node đã bị xóa"));
        when(auditLogRepository.save(any(AgentAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        TurnUndoResponse response = undoService.undoTurn(userId, convId, turnId, true);

        assertEquals(2, response.getTotalActions());
        assertEquals(0, response.getUndoneCount());
        assertEquals(1, response.getFailedCount());
        assertFalse(response.isConversationRewound());
        assertEquals(AgentAuditLog.UndoStatus.UNDO_FAILED, log2Move.getUndoStatus());
        assertEquals(AgentAuditLog.UndoStatus.UNDOABLE, log1Create.getUndoStatus());
        verify(storageGrpcClient, never()).softDeleteNode(any(), any());
        verify(chatConversationService, never()).rewindBeforeTurn(any(), any(), any());
    }

    @Test
    void undoSnapshotCaptureService_DelegatesGenericallyToAnyUndoableImplementation() {
        UndoSnapshotCaptureService captureService = new UndoSnapshotCaptureService(jsonMapper);
        Undoable customUndoable = new Undoable() {
            @Override
            public Set<String> supportedActionTypes() {
                return Set.of("CUSTOM_ACTION");
            }

            @Override
            public UndoSnapshotPayload capturePreState(UUID uid, String actionType, String targetNodeId, Object[] rawArgs) {
                return NodeMutationUndoSnapshot.builder()
                        .category(UndoSnapshotPayload.SnapshotCategory.NODE)
                        .actionType(actionType)
                        .inverseActionType(InverseActionType.RENAME_NODE)
                        .nodeId(targetNodeId)
                        .previousName("before")
                        .build();
            }

            @Override
            public String revert(UUID uid, UndoSnapshotPayload snapshot) {
                return "reverted";
            }
        };

        UndoSnapshotPayload pre = captureService.capturePreState(customUndoable, userId, "CUSTOM_ACTION", "77", new Object[]{"77"});
        assertNotNull(pre);
        String json = captureService.finalizeAndSerialize(customUndoable, userId, "CUSTOM_ACTION", pre, "ok", null);
        assertNotNull(json);
        assertTrue(json.contains("\"action_type\":\"CUSTOM_ACTION\""));
        assertTrue(json.contains("\"previous_name\":\"before\""));

        // Non-Undoable target returns null cleanly
        assertNull(captureService.capturePreState(new Object(), userId, "CUSTOM_ACTION", "77", new Object[]{"77"}));
    }
}
