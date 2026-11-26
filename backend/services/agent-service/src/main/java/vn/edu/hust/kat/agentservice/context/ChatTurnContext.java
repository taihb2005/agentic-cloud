package vn.edu.hust.kat.agentservice.context;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Holds execution metadata for a single chat turn so tool calls, GuardrailAspect,
 * and AuditService can correlate tool invocations with the active conversation, turn,
 * LLM toolCallId, and LIFO execution order.
 */
@Getter
@Setter
@Builder
public class ChatTurnContext {

    private UUID userId;
    private UUID conversationId;
    private UUID turnId;
    private int turnNumber;

    @Builder.Default
    private AtomicInteger executionOrderCounter = new AtomicInteger(0);

    private String currentToolCallId;
    private String lastCreatedResourceId;
    private String lastDeleteBatchId;

    public int nextExecutionOrder() {
        return executionOrderCounter.incrementAndGet();
    }

    public void clearLastToolArtifacts() {
        this.lastCreatedResourceId = null;
        this.lastDeleteBatchId = null;
    }
}
