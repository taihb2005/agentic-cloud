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
import dev.langchain4j.store.embedding.pgvector.DefaultMetadataStorageConfig;
import dev.langchain4j.store.embedding.pgvector.MetadataStorageConfig;
import dev.langchain4j.store.embedding.pgvector.MetadataStorageMode;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.edu.hust.kat.agentservice.core.CloudAgent;
import vn.edu.hust.kat.agentservice.memory.DualLayerMemoryManager;
import vn.edu.hust.kat.agentservice.tools.ToolRegistry;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;

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
    public EmbeddingStore<TextSegment> embeddingStore(
            DataSource dataSource,
            PgVectorProperties pgVectorProperties
    ) {
        if (pgVectorProperties.isEnabled()) {
            log.info("Initializing PgVectorEmbeddingStore (table={}, dimension={})...",
                    pgVectorProperties.getTable(), pgVectorProperties.getDimension());
            try {
                MetadataStorageConfig metadataConfig = DefaultMetadataStorageConfig.builder()
                        .storageMode(MetadataStorageMode.COMBINED_JSONB)
                        .columnDefinitions(List.of("metadata JSONB NULL"))
                        .indexes(List.of("metadata"))
                        .indexType("GIN")
                        .build();

                return PgVectorEmbeddingStore.datasourceBuilder()
                        .datasource(dataSource)
                        .table(pgVectorProperties.getTable())
                        .dimension(pgVectorProperties.getDimension())
                        .useIndex(pgVectorProperties.isUseIndex())
                        .indexListSize(pgVectorProperties.getIndexListSize())
                        .createTable(pgVectorProperties.isCreateTable())
                        .dropTableFirst(pgVectorProperties.isDropTableFirst())
                        .metadataStorageConfig(metadataConfig)
                        .build();
            } catch (Exception e) {
                log.warn("Failed to initialize PgVectorEmbeddingStore, falling back to InMemory: {}", e.getMessage(), e);
                return new InMemoryEmbeddingStore<>();
            }
        }
        log.info("Using InMemoryEmbeddingStore (app.agent.pgvector.enabled=false)");
        return new InMemoryEmbeddingStore<>();
    }

    @Bean
    public CloudAgent cloudAgent(
            ChatLanguageModel chatLanguageModel,
            StreamingChatLanguageModel streamingChatLanguageModel,
            DualLayerMemoryManager memoryManager,
            ToolRegistry toolRegistry
    ) {
        var toolExecutors = toolRegistry.getToolExecutors();
        log.info("Building LangChain4j CloudAgent with {} Tools, Dual-Layer Memory, and User-Scoped RAG...",
                toolExecutors.size());
        return AiServices.builder(CloudAgent.class)
                .chatLanguageModel(chatLanguageModel)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .chatMemoryProvider(memoryManager.getChatMemoryProvider())
                .retrievalAugmentor(memoryManager.getUserScopedRetrievalAugmentor())
                .tools(toolExecutors)
                .build();
    }
}
