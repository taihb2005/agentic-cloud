package vn.edu.hust.kat.agentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.hust.kat.agentservice.entity.AgentAuditLog;

import java.util.List;
import java.util.UUID;

@Repository
public interface AgentAuditLogRepository extends JpaRepository<AgentAuditLog, UUID> {
    List<AgentAuditLog> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
