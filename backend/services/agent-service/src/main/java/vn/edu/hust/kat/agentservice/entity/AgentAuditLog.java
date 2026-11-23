package vn.edu.hust.kat.agentservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_audit_logs", indexes = {
        @Index(name = "idx_audit_user_id", columnList = "user_id"),
        @Index(name = "idx_audit_action_type", columnList = "action_type"),
        @Index(name = "idx_audit_conversation_id", columnList = "conversation_id"),
        @Index(name = "idx_audit_turn_exec_order", columnList = "turn_id, execution_order"),
        @Index(name = "idx_audit_message_id", columnList = "message_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private ChatConversation conversation;

    @Column(name = "turn_id")
    private UUID turnId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id")
    private ChatMessage message;

    @Column(name = "tool_call_id", length = 100)
    private String toolCallId;

    @Column(name = "execution_order", nullable = false)
    @Builder.Default
    private Integer executionOrder = 0;

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
    @Enumerated(EnumType.STRING)
    private Status status; // SUCCESS, BLOCKED, FAILED, PENDING_CONFIRMATION

    @Column(name = "hitl_status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private HitlStatus hitlStatus = HitlStatus.NOT_REQUIRED;

    @Column(name = "undo_status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private UndoStatus undoStatus = UndoStatus.NOT_APPLICABLE;

    @Column(name = "undone_at")
    private Instant undoneAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "undone_by_log_id")
    private AgentAuditLog undoneByLog;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum Status {
        SUCCESS, FAILED, BLOCKED, PENDING_CONFIRMATION
    }

    public enum HitlStatus {
        NOT_REQUIRED,
        REQUIRED,
        PENDING,
        APPROVED,
        REJECTED;

        public boolean isHitlRequired() {
            return this != NOT_REQUIRED;
        }
    }

    public enum UndoStatus {
        NOT_APPLICABLE,
        UNDOABLE,
        UNDONE,
        UNDO_FAILED
    }
}
