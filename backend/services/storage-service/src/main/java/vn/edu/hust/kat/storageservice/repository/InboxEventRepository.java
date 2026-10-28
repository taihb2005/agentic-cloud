package vn.edu.hust.kat.storageservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hust.kat.storageservice.entity.InboxEvent;

import java.util.UUID;

public interface InboxEventRepository extends JpaRepository<InboxEvent, UUID> {

    boolean existsByEventIdAndConsumer(UUID eventId, String consumer);
}