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
import vn.edu.hust.kat.common.kafka.payload.UserCreatedPayload;
import vn.edu.hust.kat.storageservice.config.KafkaTopicProperties;
import vn.edu.hust.kat.storageservice.dto.request.InitUserStorageRequest;
import vn.edu.hust.kat.storageservice.dto.response.UserStorageResponse;
import vn.edu.hust.kat.storageservice.service.StorageUserService;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserCreatedConsumer {

    private final JsonMapper jsonMapper;
    private final StorageUserService storageUserService;

    @KafkaListener(
            topics = "${app.kafka.topics.user-service.user-created}",
            groupId = "storage-service-group"
    )
    public void consume(String message) {
        log.info("Nhận được message từ Kafka topic 'user.events.user-created': {}", message);
        try {
            JsonNode jsonNode = jsonMapper.readTree(message);
            String eventType = jsonNode.path("eventType").asText();

            switch (eventType) {
                case "USER_CREATED" -> {
                    BaseEvent<UserCreatedPayload> event = parseFromString(message, UserCreatedPayload.class);

                    UserCreatedPayload payload = event.getPayload();
                    log.info("Đang xử lý USER_CREATED event cho user_id: {}, email: {}", payload.getId(), payload.getEmail());

                    InitUserStorageRequest request = InitUserStorageRequest.builder()
                            .userId(UUID.fromString(payload.getId()))
                            .username(payload.getUsername())
                            .email(payload.getEmail())
                            .build();

                    UserStorageResponse response = storageUserService.initUserStorage(request);
                    log.info("Đã khởi tạo xong cây thư mục và storage cho user {}", response);
                }

                default -> log.warn("Bỏ qua event type không được hỗ trợ: {}", eventType);
            }

        } catch (Exception e) {
            log.error("Lỗi khi xử lý message từ Kafka: {}", message, e);
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
