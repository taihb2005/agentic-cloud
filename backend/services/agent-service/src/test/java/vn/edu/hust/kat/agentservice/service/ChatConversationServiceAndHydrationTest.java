package vn.edu.hust.kat.agentservice.service;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import vn.edu.hust.kat.agentservice.config.AgentMemoryProperties;
import vn.edu.hust.kat.agentservice.context.ChatTurnContext;
import vn.edu.hust.kat.agentservice.dto.conversation.AuditActionSummaryDto;
import vn.edu.hust.kat.agentservice.entity.AgentAuditLog;
import vn.edu.hust.kat.agentservice.entity.ChatConversation;
import vn.edu.hust.kat.agentservice.entity.ChatMessage;
import vn.edu.hust.kat.agentservice.memory.ChatBranchHydrationLoader;
import vn.edu.hust.kat.agentservice.memory.RedisChatMemoryStore;
import vn.edu.hust.kat.agentservice.repository.AgentAuditLogRepository;
import vn.edu.hust.kat.agentservice.repository.ChatConversationRepository;
import vn.edu.hust.kat.agentservice.repository.ChatMessageRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatConversationServiceAndHydrationTest {

    @Mock
    private ChatConversationRepository conversationRepository;

    @Mock
    private ChatMessageRepository messageRepository;

    @Mock
    private AgentAuditLogRepository auditLogRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ChatBranchHydrationLoader hydrationLoader;
    private RedisChatMemoryStore redisChatMemoryStore;
    private ChatConversationService conversationService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        AgentMemoryProperties memoryProperties = new AgentMemoryProperties();
        memoryProperties.setMaxMessages(20);

        hydrationLoader = new ChatBranchHydrationLoader(conversationRepository, messageRepository, memoryProperties);
        redisChatMemoryStore = new RedisChatMemoryStore(redisTemplate, hydrationLoader);
        conversationService = new ChatConversationService(
                conversationRepository,
                messageRepository,
                auditLogRepository,
                redisChatMemoryStore,
                hydrationLoader
        );
    }

    @Test
    void startTurn_WithoutConversationId_CreatesConversationWithAutoTitle() {
        when(conversationRepository.save(any(ChatConversation.class))).thenAnswer(inv -> {
            ChatConversation conv = inv.getArgument(0);
            if (conv.getId() == null) {
                conv.setId(UUID.randomUUID());
            }
            return conv;
        });

        ChatTurnContext turnCtx = conversationService.startTurn(userId, null, "Đổi tên file báo cáo giúp tôi");

        assertNotNull(turnCtx.getConversationId());
        assertNotNull(turnCtx.getTurnId());
        assertEquals(1, turnCtx.getTurnNumber());

        ArgumentCaptor<ChatConversation> convCaptor = ArgumentCaptor.forClass(ChatConversation.class);
        verify(conversationRepository).save(convCaptor.capture());
        assertEquals("Đổi tên file báo cáo giúp tôi", convCaptor.getValue().getTitle());
    }

    @Test
    void completeTurn_PersistsBranchChainAndBackfillsAuditMessageId() {
        UUID convId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        String memoryId = ChatConversationService.buildMemoryId(userId, convId);

        ChatConversation conv = ChatConversation.builder()
                .id(convId)
                .userId(userId)
                .title("Test")
                .status(ChatConversation.Status.ACTIVE)
                .currentTurnNumber(0)
                .lastSequenceNumber(0)
                .build();

        ToolExecutionRequest toolReq = ToolExecutionRequest.builder()
                .id("call_abc_123")
                .name("renameNode")
                .arguments("{\"nodeId\":\"10\",\"newName\":\"final.pdf\"}")
                .build();
        List<dev.langchain4j.data.message.ChatMessage> stm = List.of(
                UserMessage.from("Đổi tên file 10"),
                AiMessage.from(toolReq),
                ToolExecutionResultMessage.from(toolReq, "Thành công"),
                AiMessage.from("Mình đã đổi tên file thành final.pdf")
        );

        when(conversationRepository.findByIdAndUserIdAndStatusNot(convId, userId, ChatConversation.Status.DELETED))
                .thenReturn(Optional.of(conv));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("agent:stm:chat:" + memoryId))
                .thenReturn(ChatMessageSerializer.messagesToJson(stm));

        List<ChatMessage> persistedMessages = new ArrayList<>();
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage msg = inv.getArgument(0);
            if (msg.getId() == null) {
                msg.setId(UUID.randomUUID());
            }
            persistedMessages.add(msg);
            return msg;
        });

        AgentAuditLog auditLog = AgentAuditLog.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .turnId(turnId)
                .toolCallId("call_abc_123")
                .executionOrder(1)
                .actionType("RENAME")
                .toolName("renameNode")
                .status(AgentAuditLog.Status.SUCCESS)
                .undoStatus(AgentAuditLog.UndoStatus.UNDOABLE)
                .build();

        when(auditLogRepository.findByTurnIdAndUserIdOrderByExecutionOrderAsc(turnId, userId))
                .thenReturn(List.of(auditLog));
        when(auditLogRepository.save(any(AgentAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
        when(conversationRepository.save(any(ChatConversation.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatTurnContext ctx = ChatTurnContext.builder()
                .userId(userId)
                .conversationId(convId)
                .turnId(turnId)
                .turnNumber(1)
                .build();

        List<AuditActionSummaryDto> undoables = conversationService.completeTurn(ctx, "Đổi tên file 10", "Mình đã đổi tên file thành final.pdf");

        assertEquals(4, persistedMessages.size());
        assertEquals(ChatMessage.Role.USER, persistedMessages.get(0).getRole());
        assertEquals(ChatMessage.Role.TOOL_CALL, persistedMessages.get(1).getRole());
        assertEquals(ChatMessage.Role.TOOL_RESULT, persistedMessages.get(2).getRole());
        assertEquals(ChatMessage.Role.AI, persistedMessages.get(3).getRole());
        assertSame(persistedMessages.get(0), persistedMessages.get(1).getParentMessage());
        assertSame(persistedMessages.get(3), conv.getActiveLeafMessage());
        assertEquals(4, conv.getLastSequenceNumber());

        // Backfilled message_id points to the TOOL_CALL message
        assertSame(persistedMessages.get(1), auditLog.getMessage());
        assertEquals(1, undoables.size());
        assertEquals(persistedMessages.get(1).getId(), undoables.get(0).getMessageId());
    }

    @Test
    void redisChatMemoryStore_OnCacheMiss_HydratesActiveBranchFromPostgres() {
        UUID convId = UUID.randomUUID();
        String memoryId = ChatConversationService.buildMemoryId(userId, convId);

        ChatMessage m1 = ChatMessage.builder()
                .id(UUID.randomUUID())
                .sequenceNumber(1)
                .role(ChatMessage.Role.USER)
                .status(ChatMessage.Status.ACTIVE)
                .langchainPayload(ChatMessageSerializer.messageToJson(UserMessage.from("Xin chào")))
                .build();
        ChatMessage m2 = ChatMessage.builder()
                .id(UUID.randomUUID())
                .sequenceNumber(2)
                .parentMessage(m1)
                .role(ChatMessage.Role.AI)
                .status(ChatMessage.Status.ACTIVE)
                .langchainPayload(ChatMessageSerializer.messageToJson(AiMessage.from("Chào bạn!")))
                .build();

        ChatConversation conv = ChatConversation.builder()
                .id(convId)
                .userId(userId)
                .status(ChatConversation.Status.ACTIVE)
                .activeLeafMessage(m2)
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("agent:stm:chat:" + memoryId)).thenReturn(null);
        when(conversationRepository.findByIdAndUserIdAndStatusNot(convId, userId, ChatConversation.Status.DELETED))
                .thenReturn(Optional.of(conv));
        when(messageRepository.findByConversationIdOrderBySequenceNumberAsc(convId))
                .thenReturn(List.of(m1, m2));

        List<dev.langchain4j.data.message.ChatMessage> hydrated = redisChatMemoryStore.getMessages(memoryId);

        assertEquals(2, hydrated.size());
        assertInstanceOf(UserMessage.class, hydrated.get(0));
        assertInstanceOf(AiMessage.class, hydrated.get(1));
        verify(valueOperations).set(eq("agent:stm:chat:" + memoryId), anyString(), any(Duration.class));
    }

    @Test
    void rewindBeforeTurn_MarksTurnUndone_MovesActiveLeafAndSyncsRedis() {
        UUID convId = UUID.randomUUID();
        UUID turn1 = UUID.randomUUID();
        UUID turn2 = UUID.randomUUID();
        String memoryId = ChatConversationService.buildMemoryId(userId, convId);

        ChatMessage t1User = ChatMessage.builder()
                .id(UUID.randomUUID())
                .turnId(turn1)
                .sequenceNumber(1)
                .role(ChatMessage.Role.USER)
                .status(ChatMessage.Status.ACTIVE)
                .langchainPayload(ChatMessageSerializer.messageToJson(UserMessage.from("Turn 1")))
                .build();
        ChatMessage t1Ai = ChatMessage.builder()
                .id(UUID.randomUUID())
                .turnId(turn1)
                .sequenceNumber(2)
                .parentMessage(t1User)
                .role(ChatMessage.Role.AI)
                .status(ChatMessage.Status.ACTIVE)
                .langchainPayload(ChatMessageSerializer.messageToJson(AiMessage.from("Reply 1")))
                .build();
        ChatMessage t2User = ChatMessage.builder()
                .id(UUID.randomUUID())
                .turnId(turn2)
                .sequenceNumber(3)
                .parentMessage(t1Ai)
                .role(ChatMessage.Role.USER)
                .status(ChatMessage.Status.ACTIVE)
                .langchainPayload(ChatMessageSerializer.messageToJson(UserMessage.from("Turn 2")))
                .build();
        ChatMessage t2Ai = ChatMessage.builder()
                .id(UUID.randomUUID())
                .turnId(turn2)
                .sequenceNumber(4)
                .parentMessage(t2User)
                .role(ChatMessage.Role.AI)
                .status(ChatMessage.Status.ACTIVE)
                .langchainPayload(ChatMessageSerializer.messageToJson(AiMessage.from("Reply 2")))
                .build();

        ChatConversation conv = ChatConversation.builder()
                .id(convId)
                .userId(userId)
                .status(ChatConversation.Status.ACTIVE)
                .activeLeafMessage(t2Ai)
                .build();

        when(conversationRepository.findByIdAndUserIdAndStatusNot(convId, userId, ChatConversation.Status.DELETED))
                .thenReturn(Optional.of(conv));
        when(messageRepository.findByConversationIdOrderBySequenceNumberAsc(convId))
                .thenReturn(List.of(t1User, t1Ai, t2User, t2Ai));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        UUID newLeafId = conversationService.rewindBeforeTurn(userId, convId, turn2);

        assertEquals(t1Ai.getId(), newLeafId);
        assertSame(t1Ai, conv.getActiveLeafMessage());
        assertEquals(ChatMessage.Status.ACTIVE, t1User.getStatus());
        assertEquals(ChatMessage.Status.ACTIVE, t1Ai.getStatus());
        assertEquals(ChatMessage.Status.UNDONE, t2User.getStatus());
        assertEquals(ChatMessage.Status.UNDONE, t2Ai.getStatus());
        verify(redisTemplate).delete("agent:stm:chat:" + memoryId);
        verify(valueOperations).set(eq("agent:stm:chat:" + memoryId), anyString(), any(Duration.class));
    }
}
