package vn.edu.hust.kat.agentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.hust.kat.agentservice.entity.AgentAuditLog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentAuditLogRepository extends JpaRepository<AgentAuditLog, UUID> {
    List<AgentAuditLog> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<AgentAuditLog> findByIdAndUserId(UUID id, UUID userId);

    List<AgentAuditLog> findByConversationIdAndUserIdOrderByCreatedAtAscExecutionOrderAsc(
            UUID conversationId,
            UUID userId
    );

    List<AgentAuditLog> findByConversationIdAndTurnIdAndUserIdAndUndoStatusOrderByExecutionOrderDesc(
            UUID conversationId,
            UUID turnId,
            UUID userId,
            AgentAuditLog.UndoStatus undoStatus
    );

    List<AgentAuditLog> findByTurnIdAndUserIdOrderByExecutionOrderAsc(UUID turnId, UUID userId);

    List<AgentAuditLog> findByTurnIdAndToolCallId(UUID turnId, String toolCallId);
}
