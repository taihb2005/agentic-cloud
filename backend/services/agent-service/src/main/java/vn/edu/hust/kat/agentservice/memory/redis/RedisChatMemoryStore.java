package vn.edu.hust.kat.agentservice.memory.redis;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Short-term Chat Memory backed by Redis.
 * Stores conversation messages per user/session with TTL.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisChatMemoryStore implements ChatMemoryStore {

    private final StringRedisTemplate redisTemplate;
    private static final String KEY_PREFIX = "agent:chat_memory:";
    private static final Duration TTL = Duration.ofDays(7);

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = KEY_PREFIX + memoryId.toString();
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }
            return ChatMessageDeserializer.messagesFromJson(json);
        } catch (Exception e) {
            log.error("Failed to fetch chat memory from Redis for key {}: {}", key, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = KEY_PREFIX + memoryId.toString();
        try {
            String json = ChatMessageSerializer.messagesToJson(messages);
            redisTemplate.opsForValue().set(key, json, TTL);
        } catch (Exception e) {
            log.error("Failed to update chat memory in Redis for key {}: {}", key, e.getMessage());
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String key = KEY_PREFIX + memoryId.toString();
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("Failed to delete chat memory in Redis for key {}: {}", key, e.getMessage());
        }
    }
}
