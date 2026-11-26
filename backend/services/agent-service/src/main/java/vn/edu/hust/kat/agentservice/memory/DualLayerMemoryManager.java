package vn.edu.hust.kat.agentservice.memory;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.hust.kat.agentservice.config.AgentMemoryProperties;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DualLayerMemoryManager {

    public static final String META_USER_ID = PgVectorMemoryStore.META_USER_ID;
    public static final String META_MEMORY_TYPE = PgVectorMemoryStore.META_MEMORY_TYPE;
    public static final String META_PREF_KEY = PgVectorMemoryStore.META_PREF_KEY;
    public static final String META_CREATED_AT = PgVectorMemoryStore.META_CREATED_AT;

    private final RedisChatMemoryStore redisChatMemoryStore;
    private final PgVectorMemoryStore pgVectorMemoryStore;
    private final AgentMemoryProperties memoryProperties;

    /**
     * 1. SHORT-TERM MEMORY: Redis-backed MessageWindowChatMemory
     * Lưu ý: memoryId truyền vào theo chuẩn "{userId}:{sessionId}".
     */
    public ChatMemoryProvider getChatMemoryProvider() {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(memoryProperties.getMaxMessages())
                .chatMemoryStore(redisChatMemoryStore)
                .build();
    }

    /**
     * 2. LONG-TERM MEMORY (WRITE): Lưu quy tắc/ngữ cảnh dài hạn của User vào pgvector.
     */
    public String saveUserMemory(String userId, String memoryType, String content) {
        log.info("Save memory user {}", userId);
        try {
            return pgVectorMemoryStore.saveContext(userId, memoryType, content);
        } catch (Exception e) {
            log.error("Failed to save LTM for userId={}: {}", userId, e.getMessage(), e);
            throw new IllegalStateException("Could not persist user memory", e);
        }
    }

    /**
     * 2b. LONG-TERM MEMORY (UPSERT PREF): Cập nhật (ghi đè nếu đã có) sở thích theo prefKey của User.
     */
    public String upsertUserPreference(String userId, String prefKey, String prefValue) {
        try {
            return pgVectorMemoryStore.upsertPreference(userId, prefKey, prefValue);
        } catch (Exception e) {
            log.error("Failed to upsert user preference for userId={}, key={}: {}", userId, prefKey, e.getMessage(), e);
            throw new IllegalStateException("Could not update user preference", e);
        }
    }

    public void deleteUserPreference(String userId, String prefKey) {
        try {
            pgVectorMemoryStore.deletePreference(userId, prefKey);
        } catch (Exception e) {
            log.error("Failed to delete user preference for userId={}, key={}: {}", userId, prefKey, e.getMessage(), e);
            throw new IllegalStateException("Could not delete user preference", e);
        }
    }

    public Optional<PgVectorMemoryStore.PreferenceSnapshot> findUserPreference(String userId, String prefKey) {
        try {
            return pgVectorMemoryStore.findPreference(userId, prefKey);
        } catch (Exception e) {
            log.warn("Failed to find user preference for userId={}, key={}: {}", userId, prefKey, e.getMessage());
            return Optional.empty();
        }
    }

    public void deleteUserMemoryById(String userId, String memoryId) {
        try {
            pgVectorMemoryStore.deleteMemoryById(userId, memoryId);
        } catch (Exception e) {
            log.error("Failed to delete memory by id for userId={}, memoryId={}: {}", userId, memoryId, e.getMessage(), e);
            throw new IllegalStateException("Could not delete user memory", e);
        }
    }

    /**
     * 3. LONG-TERM MEMORY (READ): Tìm kiếm ngữ nghĩa có lọc cứng theo userId.
     */
    public List<String> searchUserSemanticMemory(String userId, String userQuery, int maxResults) {
        try {
            return pgVectorMemoryStore.searchContexts(userId, userQuery, maxResults, memoryProperties.getMinScore());
        } catch (Exception e) {
            log.error("Semantic search failed for userId={}: {}", userId, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 4. AUTO-RAG INTEGRATION: Tự động trích xuất userId từ @MemoryId ("{userId}:{sessionId}")
     * và tiêm Long-Term Memory vào Prompt của LangChain4j AiServices.
     */
    public RetrievalAugmentor getUserScopedRetrievalAugmentor() {
        return getUserScopedRetrievalAugmentor(memoryProperties.getRagMaxResults());
    }

    public RetrievalAugmentor getUserScopedRetrievalAugmentor(int maxResults) {
        ContentRetriever dynamicUserRetriever = pgVectorMemoryStore.createUserScopedContentRetriever(
                maxResults,
                memoryProperties.getMinScore()
        );

        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(dynamicUserRetriever)
                .build();
    }
}