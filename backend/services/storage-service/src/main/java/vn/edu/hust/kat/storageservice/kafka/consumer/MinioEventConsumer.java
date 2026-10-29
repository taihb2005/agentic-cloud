package vn.edu.hust.kat.storageservice.kafka.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hust.kat.storageservice.entity.FsNode;
import vn.edu.hust.kat.storageservice.entity.FsUser;
import vn.edu.hust.kat.storageservice.repository.FsNodeRepository;
import vn.edu.hust.kat.storageservice.repository.FsUserRepository;
import vn.edu.hust.kat.storageservice.service.QuotaService;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class MinioEventConsumer {

    private final FsNodeRepository fsNodeRepository;
    private final FsUserRepository fsUserRepository;
    private final QuotaService quotaService;
    private final JsonMapper jsonMapper;

    @KafkaListener(
            topics = "${app.kafka.topics.storage-service.minio-events}",
            groupId = "storage-service-minio-group"
    )
    @Transactional
    public void consume(String message) {
        log.info("Nhận được MinIO event từ Kafka: {}", message);
        try {
            JsonNode root = jsonMapper.readTree(message);

            JsonNode records = root.path("Records");
            if (records.isArray() && !records.isEmpty()) {
                for (JsonNode record : records) {
                    processRecord(record);
                }
            } else {
                processRecord(root);
            }
        } catch (Exception e) {
            log.error("Lỗi khi xử lý MinIO event message: {}", message, e);
        }
    }

    private void processRecord(JsonNode record) {
        String eventName = record.path("eventName").asText();
        if (eventName != null && !eventName.isBlank() && !eventName.contains("ObjectCreated")) {
            log.debug("Bỏ qua event không phải ObjectCreated: {}", eventName);
            return;
        }

        JsonNode s3 = record.path("s3");
        String rawKey = s3.path("object").path("key").asText();

        if (rawKey == null || rawKey.isBlank()) {
            rawKey = record.path("Key").asText();
        }

        if (rawKey == null || rawKey.isBlank()) {
            log.warn("Không tìm thấy object key trong MinIO event");
            return;
        }

        String objectKey = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);
        long size = s3.path("object").path("size").asLong(0L);
        String etag = s3.path("object").path("eTag").asText(null);

        log.info("Xử lý ObjectCreated event: key={}, size={}, etag={}", objectKey, size, etag);

        Optional<FsNode> nodeOpt = fsNodeRepository.findByS3ObjectKeyAndNodeStatus(objectKey, FsNode.Status.PENDING);
        if (nodeOpt.isEmpty()) {
            log.info("Không tìm thấy node PENDING tương ứng với s3ObjectKey: {}", objectKey);
            return;
        }

        FsNode node = nodeOpt.get();
        node.setNodeStatus(FsNode.Status.ACTIVE);
        if (etag != null && !etag.isBlank()) {
            node.setEtag(etag);
        }
        if (size > 0) {
            node.setSizeBytes(size);
        }
        node.setUpdatedAt(LocalDateTime.now());
        fsNodeRepository.save(node);

        FsUser user = node.getUser();
        if (user != null) {
            quotaService.addToUserCurrentQuota(user.getId(), size);

            log.info("Cập nhật node {} sang ACTIVE và tăng quota cho user {} thành {}",
                    node.getId(), user.getId(), user.getStorageCurrentQuotaBytes());
        }
    }
}
