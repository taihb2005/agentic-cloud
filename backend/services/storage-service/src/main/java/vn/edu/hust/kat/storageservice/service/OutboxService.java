package vn.edu.hust.kat.storageservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hust.kat.storageservice.config.KafkaTopicProperties;
import vn.edu.hust.kat.storageservice.entity.OutboxEvent;
import vn.edu.hust.kat.storageservice.kafka.event.NodePermanentDeleteEvent;
import vn.edu.hust.kat.storageservice.repository.OutboxEventRepository;
import vn.edu.hust.kat.storageservice.utils.Constants;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper;
    private final KafkaTopicProperties kafkaTopicProperties;

    /**
     * Tạo event xóa object vĩnh viễn và lưu vào cơ sở dữ liệu outbox.
     *
     * @param event Event chứa thông tin các file cần xóa trên MinIO
     */
    @Transactional
    public void createNodePermanentDeleteEvent(NodePermanentDeleteEvent event) {
        if (event == null || event.getEventId() == null) {
            throw new IllegalArgumentException("Event Id không được null");
        }

        String topic = kafkaTopicProperties.getStorageService().getNodePermanentDelete();
        UUID batchDeleteId = event.getBatchDeleteId();

        JsonNode payload = jsonMapper.valueToTree(event);

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .aggregateType(Constants.FS_NODES)
                .aggregateId(batchDeleteId.toString())
                .eventType(Constants.NODE_DELETE_PERMANENTLY)
                .topic(topic)
                .eventKey(batchDeleteId.toString())
                .payload(payload)
                .status(OutboxEvent.OutboxStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        outboxEventRepository.save(outboxEvent);

    }

    @Transactional
    public void markPublished(UUID id) {

        OutboxEvent event = outboxEventRepository.findById(id)
                .orElseThrow();

        event.setStatus(OutboxEvent.OutboxStatus.PUBLISHED);
        event.setPublishedAt(LocalDateTime.now());
        event.setLastError(null);
        event.setNextRetryAt(null);
    }

    @Transactional
    public void markFailed(UUID id, String errorMessage) {

        OutboxEvent event = outboxEventRepository.findById(id)
                .orElseThrow();

        int retryCount = event.getRetryCount() + 1;

        event.setRetryCount(retryCount);
        event.setStatus(retryCount >= 10 ? OutboxEvent.OutboxStatus.FAILED : OutboxEvent.OutboxStatus.PENDING);

        event.setLastError(errorMessage);
        event.setNextRetryAt(
                LocalDateTime.now()
                        .plusSeconds(calculateBackoff(retryCount))
        );
    }

    private long calculateBackoff(int retryCount) {
        return Math.min(60, (long) Math.pow(2, retryCount));
    }
}
