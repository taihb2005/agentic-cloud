package vn.edu.hust.kat.storageservice.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.hust.kat.storageservice.entity.OutboxEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("""
        SELECT e
        FROM OutboxEvent e
        WHERE e.status = :status
          AND (
                e.nextRetryAt IS NULL
                OR e.nextRetryAt <= :now
              )
        ORDER BY e.createdAt, e.id ASC
    """)
    List<OutboxEvent> findPublishableEvents(
            @Param("status") OutboxEvent.OutboxStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );
}
