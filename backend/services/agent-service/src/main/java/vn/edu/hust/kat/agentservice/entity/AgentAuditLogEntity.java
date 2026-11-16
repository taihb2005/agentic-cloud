package vn.edu.hust.kat.agentservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_audit_logs", indexes = {
        @Index(name = "idx_audit_user_id", columnList = "user_id"),
        @Index(name = "idx_audit_action_type", columnList = "action_type")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentAuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    @Column(name = "tool_name", nullable = false, length = 100)
    private String toolName;

    @Column(name = "target_node_id", length = 100)
    private String targetNodeId;

    @Column(name = "input_payload", columnDefinition = "TEXT")
    private String inputPayload;

    @Column(name = "undo_payload", columnDefinition = "TEXT")
    private String undoPayload;

    @Column(name = "status", nullable = false, length = 30)
    private String status; // SUCCESS, BLOCKED, FAILED, PENDING_CONFIRMATION

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
