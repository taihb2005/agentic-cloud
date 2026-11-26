package vn.edu.hust.kat.agentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hust.kat.agentservice.context.ChatTurnContext;
import vn.edu.hust.kat.agentservice.entity.AgentAuditLog;
import vn.edu.hust.kat.agentservice.entity.ChatConversation;
import vn.edu.hust.kat.agentservice.repository.AgentAuditLogRepository;
import vn.edu.hust.kat.agentservice.repository.ChatConversationRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AgentAuditLogRepository auditLogRepository;
    private final ChatConversationRepository conversationRepository;

    @Transactional
    public AgentAuditLog recordToolAction(
            UUID userId,
            String actionType,
            String toolName,
            String targetNodeId,
            String inputPayload,
            String undoPayload,
            AgentAuditLog.Status status,
            AgentAuditLog.HitlStatus hitlStatus
    ) {
        return recordToolAction(
                userId,
                null,
                actionType,
                toolName,
                targetNodeId,
                inputPayload,
                undoPayload,
                status,
                hitlStatus,
                null
        );
    }

    @Transactional
    public AgentAuditLog recordToolAction(
            UUID userId,
            String actionType,
            String toolName,
            String targetNodeId,
            String inputPayload,
            String undoPayload,
            AgentAuditLog.Status status,
            AgentAuditLog.HitlStatus hitlStatus,
            String reason
    ) {
        return recordToolAction(
                userId,
                null,
                actionType,
                toolName,
                targetNodeId,
                inputPayload,
                undoPayload,
                status,
                hitlStatus,
                reason
        );
    }

    @Transactional
    public AgentAuditLog recordToolAction(
            UUID userId,
            ChatTurnContext turnContext,
            String actionType,
            String toolName,
            String targetNodeId,
            String inputPayload,
            String undoPayload,
            AgentAuditLog.Status status,
            AgentAuditLog.HitlStatus hitlStatus,
            String reason
    ) {
        AgentAuditLog.HitlStatus effectiveHitlStatus = hitlStatus != null
                ? hitlStatus
                : AgentAuditLog.HitlStatus.NOT_REQUIRED;

        boolean hasUndoPayload = undoPayload != null && !undoPayload.isBlank();
        AgentAuditLog.UndoStatus undoStatus = (status == AgentAuditLog.Status.SUCCESS && hasUndoPayload)
                ? AgentAuditLog.UndoStatus.UNDOABLE
                : AgentAuditLog.UndoStatus.NOT_APPLICABLE;

        ChatConversation conversationRef = null;
        UUID turnId = null;
        String toolCallId = null;
        int executionOrder = 0;

        if (turnContext != null) {
            if (turnContext.getConversationId() != null && conversationRepository != null) {
                conversationRef = conversationRepository.getReferenceById(turnContext.getConversationId());
            }
            turnId = turnContext.getTurnId();
            toolCallId = turnContext.getCurrentToolCallId();
            executionOrder = turnContext.nextExecutionOrder();
        }

        log.info("Auditing action: user={}, conv={}, turn={}, order={}, type={}, tool={}, status={}, hitlStatus={}, undoStatus={}",
                userId,
                turnContext != null ? turnContext.getConversationId() : null,
                turnId,
                executionOrder,
                actionType,
                toolName,
                status,
                effectiveHitlStatus,
                undoStatus);

        AgentAuditLog logEntity = AgentAuditLog.builder()
                .userId(userId)
                .conversation(conversationRef)
                .turnId(turnId)
                .toolCallId(toolCallId)
                .executionOrder(executionOrder)
                .actionType(actionType)
                .toolName(toolName)
                .targetNodeId(targetNodeId)
                .inputPayload(inputPayload)
                .undoPayload(undoPayload)
                .status(status)
                .hitlStatus(effectiveHitlStatus)
                .undoStatus(undoStatus)
                .reason(reason)
                .build();
        return auditLogRepository.save(logEntity);
    }
}
