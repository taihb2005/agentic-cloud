package vn.edu.hust.kat.storageservice.kafka.consumer;

import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hust.kat.storageservice.config.KafkaTopicProperties;
import vn.edu.hust.kat.storageservice.entity.InboxEvent;
import vn.edu.hust.kat.storageservice.kafka.event.NodePermanentDeleteEvent;
import vn.edu.hust.kat.storageservice.repository.InboxEventRepository;
import vn.edu.hust.kat.storageservice.service.FsNodeService;
import vn.edu.hust.kat.storageservice.service.MinioStorageService;
import vn.edu.hust.kat.storageservice.utils.Constants;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class NodePermanentDeleteConsumer {

    private static final String CONSUMER_NAME = "storage-minio-delete-worker";

    private final InboxEventRepository inboxEventRepository;
    private final FsNodeService fsNodeService;
    private final MinioStorageService minioService;
    private final JsonMapper jsonMapper;

    @KafkaListener(
            topics = "${app.kafka.topics.storage-service.node-permanent-delete}",
            groupId = "storage-minio-delete-worker"
    )
    @Transactional
    public void consume(String message) {

        try {
            NodePermanentDeleteEvent event =
                    jsonMapper.readValue(
                            jsonMapper.readValue(message, String.class),
                            NodePermanentDeleteEvent.class
                    );

            UUID eventId = event.eventId();

            log.info("Xử lý yêu cầu xóa thùng rác của event {}", event);

            if (inboxEventRepository.existsByEventIdAndConsumer(eventId, CONSUMER_NAME)) {
                log.info("Event {} đã được xử lý trước đó, bỏ qua", eventId);
                return;
            }

            inboxEventRepository.save(
                    InboxEvent.builder()
                            .eventId(eventId)
                            .eventType(Constants.NODE_DELETE_PERMANENTLY)
                            .consumer(CONSUMER_NAME)
                            .processedAt(LocalDateTime.now())
                            .build()
            );

            if (event.objects() == null) {
                return;
            }

            // Xóa object trên Minio
            for (NodePermanentDeleteEvent.DeleteObject object : event.objects()) {
                minioService.deleteObject(object.bucketName(), object.objectKey());
            }

            // Xóa node trên database đặt trong transaction
            for (NodePermanentDeleteEvent.DeleteObject object : event.objects()) {
                fsNodeService.deleteNode(object.nodeId());
            }

        } catch (DataIntegrityViolationException ex) {
            log.info("Event đã được xử lý trước đó, bỏ qua");
        }
        catch (Exception ex) {
            throw new RuntimeException("Xử lý sự kiện xóa node thất bại", ex);
        }
    }
}
