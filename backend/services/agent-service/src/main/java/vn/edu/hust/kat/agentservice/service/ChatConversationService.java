package vn.edu.hust.kat.agentservice.service;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hust.kat.agentservice.context.ChatTurnContext;
import vn.edu.hust.kat.agentservice.dto.conversation.*;
import vn.edu.hust.kat.agentservice.entity.AgentAuditLog;
import vn.edu.hust.kat.agentservice.entity.ChatConversation;
import vn.edu.hust.kat.agentservice.entity.ChatMessage;
import vn.edu.hust.kat.agentservice.memory.ChatBranchHydrationLoader;
import vn.edu.hust.kat.agentservice.memory.RedisChatMemoryStore;
import vn.edu.hust.kat.agentservice.repository.AgentAuditLogRepository;
import vn.edu.hust.kat.agentservice.repository.ChatConversationRepository;
import vn.edu.hust.kat.agentservice.repository.ChatMessageRepository;
import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.common.api.exception.ResourceNotFoundException;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatConversationService {

    private static final int MAX_AUTO_TITLE_LENGTH = 60;

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final AgentAuditLogRepository auditLogRepository;
    private final RedisChatMemoryStore redisChatMemoryStore;
    private final ChatBranchHydrationLoader hydrationLoader;

    @Transactional
    public ConversationSummaryDto createConversation(UUID userId, String title) {
        String cleanTitle = (title != null && !title.isBlank()) ? title.trim() : "New conversation";
        ChatConversation conversation = ChatConversation.builder()
                .userId(userId)
                .title(cleanTitle)
                .status(ChatConversation.Status.ACTIVE)
                .currentTurnNumber(0)
                .lastSequenceNumber(0)
                .build();
        ChatConversation saved = conversationRepository.save(conversation);
        return ConversationSummaryDto.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<ConversationSummaryDto> listConversations(UUID userId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        return conversationRepository
                .findByUserIdAndStatusNotOrderByLastMessageAtDescCreatedAtDesc(
                        userId,
                        ChatConversation.Status.DELETED,
                        PageRequest.of(safePage, safeSize)
                )
                .map(ConversationSummaryDto::from);
    }

    @Transactional(readOnly = true)
    public ConversationDetailDto getConversationDetail(UUID userId, UUID conversationId) {
        ChatConversation conversation = requireOwnedConversation(userId, conversationId);
        UUID leafId = conversation.getActiveLeafMessage() != null ? conversation.getActiveLeafMessage().getId() : null;
        List<ChatMessage> branchMessages = hydrationLoader.loadActiveBranchEntities(conversationId, leafId);
        List<AgentAuditLog> auditLogs = auditLogRepository
                .findByConversationIdAndUserIdOrderByCreatedAtAscExecutionOrderAsc(conversationId, userId);

        return ConversationDetailDto.builder()
                .id(conversation.getId())
                .title(conversation.getTitle())
                .status(conversation.getStatus().name())
                .activeLeafMessageId(leafId)
                .currentTurnNumber(conversation.getCurrentTurnNumber())
                .lastMessageAt(conversation.getLastMessageAt())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .messages(branchMessages.stream().map(ChatMessageDto::from).toList())
                .auditActions(auditLogs.stream().map(AuditActionSummaryDto::from).toList())
                .build();
    }

    @Transactional
    public ConversationSummaryDto updateConversation(UUID userId, UUID conversationId, UpdateConversationRequest request) {
        ChatConversation conversation = requireOwnedConversation(userId, conversationId);
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            conversation.setTitle(request.getTitle().trim());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String upper = request.getStatus().trim().toUpperCase(Locale.ROOT);
            if ("ACTIVE".equals(upper)) {
                conversation.setStatus(ChatConversation.Status.ACTIVE);
            } else if ("ARCHIVED".equals(upper)) {
                conversation.setStatus(ChatConversation.Status.ARCHIVED);
            } else {
                throw new BadRequestException("Trạng thái hội thoại không hợp lệ (chỉ chấp nhận ACTIVE hoặc ARCHIVED)");
            }
        }
        return ConversationSummaryDto.from(conversationRepository.save(conversation));
    }

    @Transactional
    public void softDeleteConversation(UUID userId, UUID conversationId) {
        ChatConversation conversation = requireOwnedConversation(userId, conversationId);
        conversation.setStatus(ChatConversation.Status.DELETED);
        conversation.setDeletedAt(Instant.now());
        conversationRepository.save(conversation);
        redisChatMemoryStore.deleteMessages(buildMemoryId(userId, conversationId));
        log.info("Soft deleted conversation {} for user {}", conversationId, userId);
    }

    /**
     * Resolves an existing conversation (or creates a new one) and prepares a ChatTurnContext.
     */
    @Transactional
    public ChatTurnContext startTurn(UUID userId, String rawConversationId, String rawUserMessage) {
        ChatConversation conversation;
        if (rawConversationId == null || rawConversationId.isBlank()) {
            conversation = ChatConversation.builder()
                    .userId(userId)
                    .title(generateAutoTitle(rawUserMessage))
                    .status(ChatConversation.Status.ACTIVE)
                    .currentTurnNumber(0)
                    .lastSequenceNumber(0)
                    .build();
            conversation = conversationRepository.save(conversation);
        } else {
            UUID convId = parseUuid(rawConversationId, "conversation_id");
            conversation = requireOwnedConversation(userId, convId);
            if (conversation.getStatus() == ChatConversation.Status.ARCHIVED) {
                conversation.setStatus(ChatConversation.Status.ACTIVE);
            }
            if (conversation.getTitle() == null || conversation.getTitle().isBlank()
                    || "New conversation".equals(conversation.getTitle())) {
                conversation.setTitle(generateAutoTitle(rawUserMessage));
            }
            conversation = conversationRepository.save(conversation);
        }

        int nextTurnNumber = (conversation.getCurrentTurnNumber() != null ? conversation.getCurrentTurnNumber() : 0) + 1;
        return ChatTurnContext.builder()
                .userId(userId)
                .conversationId(conversation.getId())
                .turnId(UUID.randomUUID())
                .turnNumber(nextTurnNumber)
                .build();
    }

    /**
     * Persists all LangChain4j messages produced during the completed turn into PostgreSQL,
     * advances active_leaf_message_id, and backfills message_id on agent_audit_logs.
     */
    @Transactional
    public List<AuditActionSummaryDto> completeTurn(ChatTurnContext turnContext, String rawUserMessage, String finalReply) {
        if (turnContext == null || turnContext.getConversationId() == null) {
            return List.of();
        }
        UUID userId = turnContext.getUserId();
        UUID conversationId = turnContext.getConversationId();
        UUID turnId = turnContext.getTurnId();

        ChatConversation conversation = requireOwnedConversation(userId, conversationId);
        String memoryId = buildMemoryId(userId, conversationId);

        List<dev.langchain4j.data.message.ChatMessage> stmMessages = redisChatMemoryStore.getMessages(memoryId);
        List<dev.langchain4j.data.message.ChatMessage> turnMessages = extractLatestTurnMessages(stmMessages);

        if (turnMessages.isEmpty() && (rawUserMessage != null || finalReply != null)) {
            List<dev.langchain4j.data.message.ChatMessage> fallback = new ArrayList<>();
            if (rawUserMessage != null && !rawUserMessage.isBlank()) {
                fallback.add(UserMessage.from(rawUserMessage));
            }
            if (finalReply != null && !finalReply.isBlank()) {
                fallback.add(AiMessage.from(finalReply));
            }
            turnMessages = fallback;
        }

        ChatMessage parent = conversation.getActiveLeafMessage();
        int seq = conversation.getLastSequenceNumber() != null ? conversation.getLastSequenceNumber() : 0;
        Map<String, ChatMessage> toolCallMessageByCallId = new HashMap<>();

        for (dev.langchain4j.data.message.ChatMessage lcMsg : turnMessages) {
            if (lcMsg instanceof SystemMessage) {
                continue;
            }
            seq++;
            ChatMessage entity = buildMessageEntity(
                    conversation,
                    userId,
                    turnId,
                    turnContext.getTurnNumber(),
                    seq,
                    parent,
                    lcMsg,
                    rawUserMessage
            );
            entity = messageRepository.save(entity);
            parent = entity;

            if (lcMsg instanceof AiMessage aiMsg && aiMsg.hasToolExecutionRequests()) {
                for (ToolExecutionRequest req : aiMsg.toolExecutionRequests()) {
                    if (req.id() != null && !req.id().isBlank()) {
                        toolCallMessageByCallId.put(req.id(), entity);
                    }
                }
            }
        }

        conversation.setActiveLeafMessage(parent);
        conversation.setCurrentTurnNumber(turnContext.getTurnNumber());
        conversation.setLastSequenceNumber(seq);
        conversation.setLastMessageAt(Instant.now());
        conversationRepository.save(conversation);

        // Backfill message_id on audit logs recorded during this turn
        List<AgentAuditLog> turnLogs = auditLogRepository.findByTurnIdAndUserIdOrderByExecutionOrderAsc(turnId, userId);
        for (AgentAuditLog auditLog : turnLogs) {
            if (auditLog.getMessage() == null && auditLog.getToolCallId() != null) {
                ChatMessage toolCallMsg = toolCallMessageByCallId.get(auditLog.getToolCallId());
                if (toolCallMsg != null) {
                    auditLog.setMessage(toolCallMsg);
                    auditLogRepository.save(auditLog);
                }
            }
        }

        return turnLogs.stream()
                .filter(l -> l.getUndoStatus() == AgentAuditLog.UndoStatus.UNDOABLE)
                .map(AuditActionSummaryDto::from)
                .toList();
    }

    /**
     * Rewinds the active conversation branch to immediately before the specified turn,
     * marking the undone turn (and any subsequent active turns on that branch) as UNDONE,
     * and synchronizes Redis STM to match the new active branch.
     */
    @Transactional
    public UUID rewindBeforeTurn(UUID userId, UUID conversationId, UUID turnId) {
        ChatConversation conversation = requireOwnedConversation(userId, conversationId);
        UUID currentLeafId = conversation.getActiveLeafMessage() != null
                ? conversation.getActiveLeafMessage().getId()
                : null;

        List<ChatMessage> activeBranch = hydrationLoader.loadActiveBranchEntities(conversationId, currentLeafId);
        int targetStartIndex = -1;
        for (int i = 0; i < activeBranch.size(); i++) {
            if (turnId.equals(activeBranch.get(i).getTurnId())) {
                targetStartIndex = i;
                break;
            }
        }

        if (targetStartIndex < 0) {
            log.debug("Turn {} not found on active branch of conversation {}, skipping rewind", turnId, conversationId);
            return currentLeafId;
        }

        ChatMessage newLeaf = targetStartIndex > 0 ? activeBranch.get(targetStartIndex - 1) : null;
        for (int i = targetStartIndex; i < activeBranch.size(); i++) {
            ChatMessage msg = activeBranch.get(i);
            msg.setStatus(ChatMessage.Status.UNDONE);
            messageRepository.save(msg);
        }

        conversation.setActiveLeafMessage(newLeaf);
        conversationRepository.save(conversation);

        List<ChatMessage> remainingBranch = targetStartIndex > 0
                ? activeBranch.subList(0, targetStartIndex)
                : List.of();
        List<dev.langchain4j.data.message.ChatMessage> updatedStm = hydrationLoader.toLangChainMessages(remainingBranch);
        redisChatMemoryStore.evictAndReplace(buildMemoryId(userId, conversationId), updatedStm);

        UUID newLeafId = newLeaf != null ? newLeaf.getId() : null;
        log.info("Rewound conversation {} before turn {}: activeLeafMessageId -> {}", conversationId, turnId, newLeafId);
        return newLeafId;
    }

    public ChatConversation requireOwnedConversation(UUID userId, UUID conversationId) {
        return conversationRepository
                .findByIdAndUserIdAndStatusNot(conversationId, userId, ChatConversation.Status.DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hội thoại: " + conversationId));
    }

    public static String buildMemoryId(UUID userId, UUID conversationId) {
        return userId + ":" + conversationId;
    }

    private List<dev.langchain4j.data.message.ChatMessage> extractLatestTurnMessages(
            List<dev.langchain4j.data.message.ChatMessage> stmMessages
    ) {
        if (stmMessages == null || stmMessages.isEmpty()) {
            return List.of();
        }
        int lastUserIdx = -1;
        for (int i = stmMessages.size() - 1; i >= 0; i--) {
            if (stmMessages.get(i) instanceof UserMessage) {
                lastUserIdx = i;
                break;
            }
        }
        if (lastUserIdx < 0) {
            return List.of();
        }
        return stmMessages.subList(lastUserIdx, stmMessages.size());
    }

    private ChatMessage buildMessageEntity(
            ChatConversation conversation,
            UUID userId,
            UUID turnId,
            int turnNumber,
            int sequenceNumber,
            ChatMessage parent,
            dev.langchain4j.data.message.ChatMessage lcMsg,
            String rawUserMessage
    ) {
        ChatMessage.Role role;
        String content;
        String toolCallId = null;
        String toolName = null;

        if (lcMsg instanceof UserMessage userMsg) {
            role = ChatMessage.Role.USER;
            content = (rawUserMessage != null && !rawUserMessage.isBlank())
                    ? rawUserMessage
                    : userMsg.singleText();
        } else if (lcMsg instanceof AiMessage aiMsg) {
            if (aiMsg.hasToolExecutionRequests()) {
                role = ChatMessage.Role.TOOL_CALL;
                ToolExecutionRequest firstReq = aiMsg.toolExecutionRequests().get(0);
                toolCallId = firstReq.id();
                toolName = firstReq.name();
                content = aiMsg.text() != null ? aiMsg.text() : firstReq.arguments();
            } else {
                role = ChatMessage.Role.AI;
                content = aiMsg.text();
            }
        } else if (lcMsg instanceof ToolExecutionResultMessage toolResultMsg) {
            role = ChatMessage.Role.TOOL_RESULT;
            toolCallId = toolResultMsg.id();
            toolName = toolResultMsg.toolName();
            content = toolResultMsg.text();
        } else {
            role = ChatMessage.Role.SYSTEM;
            content = lcMsg.toString();
        }

        String serializedPayload = ChatMessageSerializer.messageToJson(lcMsg);

        return ChatMessage.builder()
                .conversation(conversation)
                .userId(userId)
                .turnId(turnId)
                .turnNumber(turnNumber)
                .sequenceNumber(sequenceNumber)
                .parentMessage(parent)
                .role(role)
                .content(content)
                .langchainPayload(serializedPayload)
                .toolCallId(toolCallId)
                .toolName(toolName)
                .status(ChatMessage.Status.ACTIVE)
                .build();
    }

    private String generateAutoTitle(String rawUserMessage) {
        if (rawUserMessage == null || rawUserMessage.isBlank()) {
            return "Untilted";
        }
        String singleLine = rawUserMessage.trim().replaceAll("\\s+", " ");
        if (singleLine.length() <= MAX_AUTO_TITLE_LENGTH) {
            return singleLine;
        }
        return singleLine.substring(0, MAX_AUTO_TITLE_LENGTH - 3) + "...";
    }

    private UUID parseUuid(String raw, String fieldName) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(fieldName + " không đúng định dạng UUID: " + raw);
        }
    }
}
