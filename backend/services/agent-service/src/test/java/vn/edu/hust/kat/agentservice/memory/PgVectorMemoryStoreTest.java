package vn.edu.hust.kat.agentservice.memory;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PgVectorMemoryStoreTest {

    private InMemoryEmbeddingStore<TextSegment> embeddingStore;
    private PgVectorMemoryStore memoryStore;

    @BeforeEach
    void setUp() {
        embeddingStore = new InMemoryEmbeddingStore<>();
        EmbeddingModel deterministicModel = textSegments -> {
            List<Embedding> embeddings = textSegments.stream()
                    .map(segment -> embedText(segment.text()))
                    .toList();
            return Response.from(embeddings);
        };
        memoryStore = new PgVectorMemoryStore(embeddingStore, deterministicModel);
    }

    @Test
    void upsertPreference_ReplacesExistingPreferenceForSameUserAndKey() {
        memoryStore.upsertPreference("user-1", "Default Invoice Folder", "/Invoices/2025");
        memoryStore.upsertPreference("user-1", "default_invoice_folder", "/Finance/2026");
        memoryStore.upsertPreference("user-2", "default_invoice_folder", "/OtherUser");

        List<String> user1Results = memoryStore.searchContexts("user-1", "invoice folder", 5, 0.0);
        assertEquals(1, user1Results.size());
        assertTrue(user1Results.get(0).contains("/Finance/2026"));
        assertFalse(user1Results.get(0).contains("/Invoices/2025"));

        List<String> user2Results = memoryStore.searchContexts("user-2", "invoice folder", 5, 0.0);
        assertEquals(1, user2Results.size());
        assertTrue(user2Results.get(0).contains("/OtherUser"));
    }

    @Test
    void deletePreference_RemovesOnlyMatchingUserAndKey() {
        memoryStore.upsertPreference("user-1", "response_language", "Vietnamese");
        memoryStore.upsertPreference("user-1", "summary_style", "Bullet points");

        memoryStore.deletePreference("user-1", "response_language");

        List<String> results = memoryStore.searchContexts("user-1", "language summary", 5, 0.0);
        assertEquals(1, results.size());
        assertTrue(results.get(0).contains("summary_style"));
    }

    @Test
    void createUserScopedContentRetriever_IsolatesByUserIdFromMemoryId() {
        memoryStore.saveContext("user-a", "USER_FACT", "Tôi là kế toán trưởng");
        memoryStore.saveContext("user-b", "USER_FACT", "Tôi là lập trình viên backend");

        ContentRetriever retriever = memoryStore.createUserScopedContentRetriever(5, 0.0);
        Query query = Query.from(
                "người dùng làm nghề gì",
                Metadata.from(UserMessage.from("người dùng làm nghề gì"), "user-a:session-1", List.of())
        );

        List<Content> retrieved = retriever.retrieve(query);
        assertEquals(1, retrieved.size());
        assertTrue(retrieved.get(0).textSegment().text().contains("kế toán trưởng"));
    }

    @Test
    void createUserScopedContentRetriever_ThrowsWhenMemoryIdIsNull() {
        ContentRetriever retriever = memoryStore.createUserScopedContentRetriever(5, 0.0);
        Query query = Query.from("test");

        assertThrows(IllegalArgumentException.class, () -> retriever.retrieve(query));
    }

    private Embedding embedText(String text) {
        String lower = text.toLowerCase();
        float f0 = lower.contains("invoice") || lower.contains("folder") || lower.contains("finance") ? 1.0f : 0.2f;
        float f1 = lower.contains("language") || lower.contains("summary") ? 1.0f : 0.2f;
        float f2 = lower.contains("kế toán") || lower.contains("lập trình") || lower.contains("nghề") ? 1.0f : 0.2f;
        return Embedding.from(new float[]{f0, f1, f2, 0.5f});
    }
}
