package vn.edu.hust.kat.agentservice.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Slf4j
public class RedisChatMemoryStore implements ChatMemoryStore {

    private static final String KEY_PREFIX = "agent:stm:chat:";
    private final StringRedisTemplate redisTemplate;

    @Value("${app.agent.memory.session-ttl-minutes}")
    private long sessionTtlMinutes;

    private String buildKey(Object memoryId) {
        return KEY_PREFIX + memoryId.toString();
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        log.info("Getting messages for memoryId: {}", memoryId.toString());
        String key = buildKey(memoryId);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return List.of();
        }
        redisTemplate.expire(key, Duration.ofMinutes(sessionTtlMinutes));
        return ChatMessageDeserializer.messagesFromJson(json);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = buildKey(memoryId);
        String json = ChatMessageSerializer.messagesToJson(messages);
        redisTemplate.opsForValue().set(key, json, Duration.ofMinutes(sessionTtlMinutes));
    }

    @Override
    public void deleteMessages(Object memoryId) {
        redisTemplate.delete(buildKey(memoryId));
    }
}