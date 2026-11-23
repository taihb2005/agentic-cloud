package vn.edu.hust.kat.agentservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_msg_conv_seq", columnList = "conversation_id, sequence_number"),
        @Index(name = "idx_chat_msg_conv_turn", columnList = "conversation_id, turn_id"),
        @Index(name = "idx_chat_msg_turn_id", columnList = "turn_id"),
        @Index(name = "idx_chat_msg_parent_id", columnList = "parent_message_id"),
        @Index(name = "idx_chat_msg_user_id", columnList = "user_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ChatConversation conversation;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "turn_id", nullable = false)
    private UUID turnId;

    @Column(name = "turn_number", nullable = false)
    private Integer turnNumber;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_message_id")
    private ChatMessage parentMessage;

    @OneToMany(mappedBy = "parentMessage")
    @Builder.Default
    private List<ChatMessage> childMessages = new ArrayList<>();

    @Column(name = "role", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "langchain_payload", columnDefinition = "TEXT")
    private String langchainPayload;

    @Column(name = "tool_call_id", length = 100)
    private String toolCallId;

    @Column(name = "tool_name", length = 100)
    private String toolName;

    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum Role {
        SYSTEM,
        USER,
        AI,
        TOOL_CALL,
        TOOL_RESULT
    }

    public enum Status {
        ACTIVE,
        UNDONE,
        SUPERSEDED,
        FAILED
    }
}
