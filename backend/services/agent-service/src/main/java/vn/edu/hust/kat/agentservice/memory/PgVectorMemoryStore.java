package vn.edu.hust.kat.agentservice.memory;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * Long-term Memory Store backed by pgvector (via {@link EmbeddingStore}).
 * Persists user-scoped contexts, habits, facts, and mutable user preferences.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class PgVectorMemoryStore {

    public static final String META_USER_ID = "user_id";
    public static final String META_MEMORY_TYPE = "memory_type";
    public static final String META_PREF_KEY = "pref_key";
    public static final String META_CREATED_AT = "created_at";
    public static final String META_UPDATED_AT = "updated_at";

    public static final String TYPE_USER_PREFERENCE = "USER_PREFERENCE";

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    public String saveContext(String userId, String memoryType, String content) {
        validateNonBlank(userId, "userId");
        validateNonBlank(memoryType, "memoryType");
        validateNonBlank(content, "content");

        String now = Instant.now().toString();
        String normalizedType = memoryType.trim().toUpperCase(Locale.ROOT);

        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put(META_USER_ID, userId);
        metaMap.put(META_MEMORY_TYPE, normalizedType);
        metaMap.put(META_CREATED_AT, now);
        metaMap.put(META_UPDATED_AT, now);

        TextSegment segment = TextSegment.from(content.trim(), Metadata.from(metaMap));
        Embedding embedding = embeddingModel.embed(segment).content();

        String id = embeddingStore.add(embedding, segment);
        log.info("Saved long-term context: userId={}, type={}, id={}", userId, normalizedType, id);
        return id;
    }

    public String upsertPreference(String userId, String prefKey, String prefValue) {
        validateNonBlank(userId, "userId");
        validateNonBlank(prefValue, "prefValue");
        String normalizedKey = normalizePrefKey(prefKey);

        Filter existingPrefFilter = metadataKey(META_USER_ID).isEqualTo(userId)
                .and(metadataKey(META_PREF_KEY).isEqualTo(normalizedKey));
        embeddingStore.removeAll(existingPrefFilter);

        String now = Instant.now().toString();
        String formattedContent = String.format("User preference [%s]: %s", normalizedKey, prefValue.trim());

        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put(META_USER_ID, userId);
        metaMap.put(META_MEMORY_TYPE, TYPE_USER_PREFERENCE);
        metaMap.put(META_PREF_KEY, normalizedKey);
        metaMap.put(META_CREATED_AT, now);
        metaMap.put(META_UPDATED_AT, now);

        TextSegment segment = TextSegment.from(formattedContent, Metadata.from(metaMap));
        Embedding embedding = embeddingModel.embed(segment).content();

        String id = embeddingStore.add(embedding, segment);
        log.info("Upserted user preference: userId={}, prefKey={}, id={}", userId, normalizedKey, id);
        return id;
    }

    public void deletePreference(String userId, String prefKey) {
        validateNonBlank(userId, "userId");
        String normalizedKey = normalizePrefKey(prefKey);

        Filter prefFilter = metadataKey(META_USER_ID).isEqualTo(userId)
                .and(metadataKey(META_PREF_KEY).isEqualTo(normalizedKey));
        embeddingStore.removeAll(prefFilter);
        log.info("Deleted user preference: userId={}, prefKey={}", userId, normalizedKey);
    }

    public void deleteUserContexts(String userId) {
        validateNonBlank(userId, "userId");
        embeddingStore.removeAll(metadataKey(META_USER_ID).isEqualTo(userId));
        log.info("Deleted all long-term contexts for userId={}", userId);
    }

    public List<String> searchContexts(String userId, String query, int maxResults, double minScore) {
        validateNonBlank(userId, "userId");
        if (query == null || query.isBlank()) {
            return List.of();
        }

        Embedding queryEmbedding = embeddingModel.embed(query.trim()).content();
        Filter userFilter = metadataKey(META_USER_ID).isEqualTo(userId);

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .filter(userFilter)
                .build();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
        return result.matches().stream()
                .map(match -> formatMatch(match.embedded()))
                .toList();
    }

    public ContentRetriever createUserScopedContentRetriever(int maxResults, double minScore) {
        EmbeddingStoreContentRetriever delegate = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(maxResults)
                .minScore(minScore)
                .dynamicFilter(query -> {
                    Object memoryIdObj = query.metadata() != null ? query.metadata().chatMemoryId() : null;
                    String userId = extractUserId(memoryIdObj);
                    return metadataKey(META_USER_ID).isEqualTo(userId);
                })
                .build();

        return query -> {
            Object memoryIdObj = query.metadata() != null ? query.metadata().chatMemoryId() : null;
            // Enforce tenant/user isolation before attempting retrieval
            extractUserId(memoryIdObj);
            try {
                List<Content> contents = delegate.retrieve(query);
                return contents != null ? contents : List.of();
            } catch (Exception e) {
                log.warn("Long-term memory retrieval skipped due to error: {}", e.getMessage());
                return List.of();
            }
        };
    }

    public static String normalizePrefKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new IllegalArgumentException("prefKey cannot be blank");
        }
        String normalized = rawKey.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("prefKey must contain alphanumeric characters");
        }
        return normalized;
    }

    private String formatMatch(TextSegment segment) {
        if (segment == null) {
            return "";
        }
        String type = segment.metadata().getString(META_MEMORY_TYPE);
        String prefKey = segment.metadata().getString(META_PREF_KEY);
        if (prefKey != null && !prefKey.isBlank()) {
            return String.format("[%s:%s] %s", type, prefKey, segment.text());
        }
        return String.format("[%s] %s", type != null ? type : "CONTEXT", segment.text());
    }

    private String extractUserId(Object memoryIdObj) {
        if (memoryIdObj == null || memoryIdObj.toString().isBlank()) {
            throw new IllegalArgumentException("chatMemoryId cannot be null or blank for user-scoped RAG");
        }
        String raw = memoryIdObj.toString();
        int colonIdx = raw.indexOf(':');
        return colonIdx > 0 ? raw.substring(0, colonIdx) : raw;
    }

    private void validateNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
    }
}
