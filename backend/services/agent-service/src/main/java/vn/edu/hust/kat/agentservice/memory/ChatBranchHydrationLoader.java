package vn.edu.hust.kat.agentservice.memory;

import dev.langchain4j.data.message.ChatMessageDeserializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hust.kat.agentservice.config.AgentMemoryProperties;
import vn.edu.hust.kat.agentservice.entity.ChatConversation;
import vn.edu.hust.kat.agentservice.entity.ChatMessage;
import vn.edu.hust.kat.agentservice.repository.ChatConversationRepository;
import vn.edu.hust.kat.agentservice.repository.ChatMessageRepository;

import java.util.*;

/**
 * Loads the active message branch (root -> active_leaf_message_id) from PostgreSQL
 * to hydrate Redis Short-Term Memory on cache miss or after conversation rewind.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatBranchHydrationLoader {

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final AgentMemoryProperties memoryProperties;

    @Transactional(readOnly = true)
    public List<ChatMessage> loadActiveBranchEntities(UUID conversationId, UUID activeLeafMessageId) {
        if (conversationId == null || activeLeafMessageId == null) {
            return List.of();
        }

        // Lấy message từ database
        List<ChatMessage> allMessages = messageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationId);
        if (allMessages.isEmpty()) {
            return List.of();
        }

        Map<UUID, ChatMessage> byId = new HashMap<>(allMessages.size());
        for (ChatMessage msg : allMessages) {
            byId.put(msg.getId(), msg);
        }

        LinkedList<ChatMessage> branch = new LinkedList<>();
        Set<UUID> visited = new HashSet<>();
        UUID cursorId = activeLeafMessageId;

        while (cursorId != null && visited.add(cursorId)) {
            ChatMessage node = byId.get(cursorId);
            if (node == null) {
                break;
            }
            if (node.getStatus() == ChatMessage.Status.ACTIVE) {
                branch.addFirst(node);
            }
            cursorId = node.getParentMessage() != null ? node.getParentMessage().getId() : null;
        }

        return branch;
    }

    @Transactional(readOnly = true)
    public List<dev.langchain4j.data.message.ChatMessage> hydrateLangChainMessages(Object memoryId) {
        if (memoryId == null) {
            return List.of();
        }
        String raw = memoryId.toString();
        int colonIdx = raw.indexOf(':');
        if (colonIdx <= 0 || colonIdx >= raw.length() - 1) {
            return List.of();
        }

        UUID userId;
        UUID conversationId;
        try {
            userId = UUID.fromString(raw.substring(0, colonIdx).trim());
            conversationId = UUID.fromString(raw.substring(colonIdx + 1).trim());
        } catch (IllegalArgumentException e) {
            return List.of();
        }

        Optional<ChatConversation> convOpt = conversationRepository.findByIdAndUserIdAndStatusNot(
                conversationId,
                userId,
                ChatConversation.Status.DELETED
        );
        if (convOpt.isEmpty() || convOpt.get().getActiveLeafMessage() == null) {
            return List.of();
        }

        UUID leafId = convOpt.get().getActiveLeafMessage().getId();
        List<ChatMessage> activeEntities = loadActiveBranchEntities(conversationId, leafId);
        return toLangChainMessages(activeEntities);
    }

    public List<dev.langchain4j.data.message.ChatMessage> toLangChainMessages(List<ChatMessage> activeEntities) {
        if (activeEntities == null || activeEntities.isEmpty()) {
            return List.of();
        }

        List<dev.langchain4j.data.message.ChatMessage> result = new ArrayList<>();
        for (ChatMessage entity : activeEntities) {
            String payload = entity.getLangchainPayload();
            if (payload == null || payload.isBlank()) {
                continue;
            }
            try {
                result.add(ChatMessageDeserializer.messageFromJson(payload));
            } catch (Exception e) {
                log.warn("Skipping undeserializable langchain_payload for message {}: {}", entity.getId(), e.getMessage());
            }
        }

        int maxMessages = memoryProperties.getMaxMessages();
        if (result.size() > maxMessages) {
            return new ArrayList<>(result.subList(result.size() - maxMessages, result.size()));
        }
        return result;
    }
}
