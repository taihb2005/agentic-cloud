package vn.edu.hust.kat.agentservice.memory;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.edu.hust.kat.agentservice.memory.redis.RedisChatMemoryStore;

import java.util.List;

/**
 * Dual-Layer Memory Manager:
 * 1. Short-term Memory: Redis-backed MessageWindowChatMemory
 * 2. Long-term Semantic Memory: Postgres pgvector EmbeddingStore (RAG)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DualLayerMemoryManager {

    private final RedisChatMemoryStore redisChatMemoryStore;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    @Value("${app.agent.memory.max-messages:20}")
    private int maxMessages;

    /**
     * ChatMemoryProvider for LangChain4j Agent
     */
    public ChatMemoryProvider getChatMemoryProvider() {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(maxMessages)
                .chatMemoryStore(redisChatMemoryStore)
                .build();
    }

    /**
     * Semantic / RAG search against PostgreSQL pgvector
     */
    public List<String> searchSemantic(String userQuery, int maxResults) {
        try {
            Embedding queryEmbedding = embeddingModel.embed(userQuery).content();
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults)
                    .minScore(0.65)
                    .build();

            EmbeddingSearchResult<TextSegment> result = embeddingStore.search(searchRequest);
            return result.matches().stream()
                    .map(match -> match.embedded().text())
                    .toList();
        } catch (Exception e) {
            log.error("Semantic search failed: {}", e.getMessage());
            return List.of();
        }
    }
}
