package vn.edu.hust.kat.storageservice.kafka.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hust.kat.storageservice.entity.OutboxEvent;
import vn.edu.hust.kat.storageservice.repository.OutboxEventRepository;
import vn.edu.hust.kat.storageservice.service.OutboxService;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxService outboxService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    public void publish() {

        List<OutboxEvent> events = outboxEventRepository.findPublishableEvents(
                        OutboxEvent.OutboxStatus.PENDING,
                        LocalDateTime.now(),
                        PageRequest.of(0, 100)
                );

        for (OutboxEvent event : events) {
            publish(event);
        }
    }


    private void publish(OutboxEvent event) {

        try {
            kafkaTemplate.send(
                event.getTopic(),
                event.getEventKey(),
                event.getPayload().toString()
            ).whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Event outbox gửi thành công: {}", result.getRecordMetadata().offset());
                } else {
                    log.error("Gửi event outbox thất bại: {}", ex.getMessage());
                }
            });;

            outboxService.markPublished(event.getId());

        } catch (Exception ex) {
            outboxService.markFailed(event.getId(), ex.getMessage());
        }
    }

    @Transactional
    protected void markPublished(OutboxEvent event) {

        event.setStatus(OutboxEvent.OutboxStatus.PUBLISHED);

        event.setPublishedAt(LocalDateTime.now());

        event.setLastError(null);

        outboxEventRepository.save(event);
    }

    @Transactional
    protected void markFailed(OutboxEvent event, Exception ex) {

        int retryCount = event.getRetryCount() + 1;

        event.setRetryCount(retryCount);

        event.setStatus(retryCount >= 10 ? OutboxEvent.OutboxStatus.FAILED : OutboxEvent.OutboxStatus.PENDING);

        event.setLastError(ex.getMessage());

        event.setNextRetryAt(LocalDateTime.now().plusSeconds(calculateBackoff(retryCount)));

        outboxEventRepository.save(event);
    }

    private long calculateBackoff(int retryCount) {
        return Math.min(60, (long) Math.pow(2, retryCount));
    }
}
