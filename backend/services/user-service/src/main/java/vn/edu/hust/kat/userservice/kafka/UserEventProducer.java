package vn.edu.hust.kat.userservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import vn.edu.hust.kat.common.kafka.event.BaseEvent;
import vn.edu.hust.kat.common.kafka.payload.UserCreatedPayload;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.user-service.user-created}")
    private String userCreatedTopic;

    public void publishEvent(BaseEvent<UserCreatedPayload> event) {
        log.info("Sending event UserCreatedEvent: {}", event.getPayload());

        UserCreatedPayload payload = event.getPayload();

        kafkaTemplate.send(
                    userCreatedTopic,
                    payload.getId(),
                    event
                )
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Event sent successfully with offset: {}", result.getRecordMetadata().offset());
                    } else {
                        log.error("Failed to send event: {}", ex.getMessage());
                    }
                });
    }
}