package vn.edu.hust.kat.storageservice.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import vn.edu.hust.kat.common.kafka.event.BaseEvent;
import vn.edu.hust.kat.common.kafka.payload.UserDeletedPayload;
import vn.edu.hust.kat.storageservice.service.StorageUserService;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserDeletedConsumer {

    private final JsonMapper jsonMapper;
    private final StorageUserService storageUserService;

    @KafkaListener(
            topics = "${app.kafka.topics.user-service.user-deleted}",
            groupId = "storage-service-group"
    )
    public void consume(String message) {
        log.info("Nhận được message từ Kafka topic 'user.events.user-deleted': {}", message);
        try {
            JsonNode jsonNode = jsonMapper.readTree(message);
            String eventType = jsonNode.has("eventType")
                    ? jsonNode.path("eventType").asText()
                    : jsonNode.path("event_type").asText();

            switch (eventType) {
                case "USER_DELETED" -> {
                    BaseEvent<UserDeletedPayload> event = parseFromString(message, UserDeletedPayload.class);

                    UserDeletedPayload payload = event.getPayload();
                    log.info("Đang xử lý USER_DELETED event (hard delete) cho user_id: {}, email: {}",
                            payload.getId(), payload.getEmail());

                    UUID userId = UUID.fromString(payload.getId());
                    storageUserService.deleteUserStorage(userId);
                    log.info("Đã hoàn tất xóa cứng toàn bộ dữ liệu cloud cho user_id: {}", userId);
                }

                default -> log.warn("Bỏ qua event type không được hỗ trợ trên topic user-deleted: {}", eventType);
            }

        } catch (Exception e) {
            log.error("Lỗi khi xử lý message xóa user từ Kafka: {}", message, e);
        }
    }

    private <T> BaseEvent<T> parseFromString(
            String json,
            Class<T> payloadClass
    ) throws JsonProcessingException {

        JavaType type = jsonMapper.getTypeFactory()
                .constructParametricType(BaseEvent.class, payloadClass);

        return jsonMapper.readValue(json, type);
    }
}
