package vn.edu.hust.kat.agentservice.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

@Repository
@Slf4j
public class RedisChatMemoryStore implements ChatMemoryStore {

    private static final String KEY_PREFIX = "agent:stm:chat:";
    private final StringRedisTemplate redisTemplate;
    private final ChatBranchHydrationLoader hydrationLoader;

    @Value("${app.agent.memory.session-ttl-minutes:45}")
    private long sessionTtlMinutes = 45L;

    @Autowired
    public RedisChatMemoryStore(
            StringRedisTemplate redisTemplate,
            ChatBranchHydrationLoader hydrationLoader
    ) {
        this.redisTemplate = redisTemplate;
        this.hydrationLoader = hydrationLoader;
    }

    private String buildKey(Object memoryId) {
        return KEY_PREFIX + memoryId.toString();
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = buildKey(memoryId);
        String json = readRedisSafe(key);
        if (json != null && !json.isBlank()) {
            refreshTtlSafe(key);
            return ChatMessageDeserializer.messagesFromJson(json);
        }

        if (hydrationLoader != null) {
            List<ChatMessage> hydrated = hydrationLoader.hydrateLangChainMessages(memoryId);
            if (!hydrated.isEmpty()) {
                log.info("Hydrated {} messages from PostgreSQL into Redis STM for memoryId={}", hydrated.size(), memoryId);
                updateMessages(memoryId, hydrated);
                return hydrated;
            }
        }
        return List.of();
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = buildKey(memoryId);
        if (messages == null || messages.isEmpty()) {
            deleteMessages(memoryId);
            return;
        }
        String json = ChatMessageSerializer.messagesToJson(messages);
        writeRedisSafe(key, json);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.delete(buildKey(memoryId));
        } catch (Exception e) {
            log.warn("Failed to delete Redis STM for memoryId={}: {}", memoryId, e.getMessage());
        }
    }

    public void evictAndReplace(Object memoryId, List<ChatMessage> activeMessages) {
        deleteMessages(memoryId);
        if (activeMessages != null && !activeMessages.isEmpty()) {
            updateMessages(memoryId, activeMessages);
        }
    }

    private String readRedisSafe(String key) {
        if (redisTemplate == null) {
            return null;
        }
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis read failed for key={}: {}", key, e.getMessage());
            return null;
        }
    }

    private void writeRedisSafe(String key, String json) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, json, Duration.ofMinutes(sessionTtlMinutes));
        } catch (Exception e) {
            log.warn("Redis write failed for key={}: {}", key, e.getMessage());
        }
    }

    private void refreshTtlSafe(String key) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.expire(key, Duration.ofMinutes(sessionTtlMinutes));
        } catch (Exception e) {
            log.debug("Redis expire failed for key={}: {}", key, e.getMessage());
        }
    }
}
