package vn.edu.hust.kat.agentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hust.kat.agentservice.entity.AgentAuditLog;
import vn.edu.hust.kat.agentservice.repository.AgentAuditLogRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AgentAuditLogRepository auditLogRepository;

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
        AgentAuditLog.HitlStatus effectiveHitlStatus = hitlStatus != null
                ? hitlStatus
                : AgentAuditLog.HitlStatus.NOT_REQUIRED;
        log.info("Auditing action: user={}, type={}, tool={}, status={}, hitlStatus={}",
                userId, actionType, toolName, status, effectiveHitlStatus);
        AgentAuditLog logEntity = AgentAuditLog.builder()
                .userId(userId)
                .actionType(actionType)
                .toolName(toolName)
                .targetNodeId(targetNodeId)
                .inputPayload(inputPayload)
                .undoPayload(undoPayload)
                .status(status)
                .hitlStatus(effectiveHitlStatus)
                .reason(reason)
                .build();
        return auditLogRepository.save(logEntity);
    }
}
