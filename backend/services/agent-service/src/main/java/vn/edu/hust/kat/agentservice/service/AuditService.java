package vn.edu.hust.kat.agentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hust.kat.agentservice.entity.AgentAuditLogEntity;
import vn.edu.hust.kat.agentservice.repository.AgentAuditLogRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AgentAuditLogRepository auditLogRepository;

    @Transactional
    public AgentAuditLogEntity recordToolAction(
            UUID userId,
            String actionType,
            String toolName,
            String targetNodeId,
            String inputPayload,
            String undoPayload,
            String status
    ) {
        String safeStatus = (status != null && status.length() > 30)
                ? status.substring(0, 30)
                : status;
        log.info("Auditing action: user={}, type={}, tool={}, status={}", userId, actionType, toolName, safeStatus);
        AgentAuditLogEntity logEntity = AgentAuditLogEntity.builder()
                .userId(userId)
                .actionType(actionType)
                .toolName(toolName)
                .targetNodeId(targetNodeId)
                .inputPayload(inputPayload)
                .undoPayload(undoPayload)
                .status(safeStatus)
                .build();
        return auditLogRepository.save(logEntity);
    }
}
