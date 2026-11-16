package vn.edu.hust.kat.agentservice.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.edu.hust.kat.agentservice.core.CloudAgent;
import vn.edu.hust.kat.agentservice.memory.DualLayerMemoryManager;
import vn.edu.hust.kat.agentservice.tools.ToolRegistry;

import java.time.Duration;

@Configuration
@Slf4j
public class CognitiveConfig {

    @Value("${app.llm.openai.api-key:demo}")
    private String apiKey;

    @Value("${app.llm.openai.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${app.llm.openai.model-name:gpt-4o-mini}")
    private String modelName;

    @Value("${app.llm.openai.embedding-model:text-embedding-3-small}")
    private String embeddingModelName;

    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/agentic_cloud}")
    private String dbUrl;

    @Value("${spring.datasource.username:postgres}")
    private String dbUsername;

    @Value("${spring.datasource.password:postgres}")
    private String dbPassword;

    @Value("${app.agent.pgvector.enabled:false}")
    private boolean pgvectorEnabled;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        log.info("Initializing ChatLanguageModel with model: {}", modelName);
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        log.info("Initializing StreamingChatLanguageModel with model: {}", modelName);
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("Initializing EmbeddingModel with model: {}", embeddingModelName);
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(embeddingModelName)
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        if (pgvectorEnabled) {
            log.info("Initializing PgVectorEmbeddingStore connecting to PostgreSQL...");
            try {
                // Parse host, port, database from dbUrl or use PgVector default builder
                return PgVectorEmbeddingStore.datasourceBuilder()
                        .table("document_vector_store")
                        .dimension(1536)
                        .build();
            } catch (Exception e) {
                log.warn("Failed to initialize PgVectorEmbeddingStore, falling back to InMemory: {}", e.getMessage());
                return new InMemoryEmbeddingStore<>();
            }
        }
        log.info("Using InMemoryEmbeddingStore for development/testing");
        return new InMemoryEmbeddingStore<>();
    }

    @Bean
    public CloudAgent cloudAgent(
            ChatLanguageModel chatLanguageModel,
            StreamingChatLanguageModel streamingChatLanguageModel,
            DualLayerMemoryManager memoryManager,
            ToolRegistry toolRegistry
    ) {
        log.info("Building LangChain4j CloudAgent with Tools and Dual-Layer Memory...");
        return AiServices.builder(CloudAgent.class)
                .chatLanguageModel(chatLanguageModel)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .chatMemoryProvider(memoryManager.getChatMemoryProvider())
                .tools(toolRegistry.getAllTools())
                .build();
    }
}
