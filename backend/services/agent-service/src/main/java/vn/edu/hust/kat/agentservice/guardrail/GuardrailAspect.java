package vn.edu.hust.kat.agentservice.guardrail;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.edu.hust.kat.agentservice.context.UserContextManager;
import vn.edu.hust.kat.agentservice.grpc.StorageGrpcClient;
import vn.edu.hust.kat.agentservice.guardrail.annotation.ReadAction;
import vn.edu.hust.kat.agentservice.guardrail.annotation.SafeAction;
import vn.edu.hust.kat.agentservice.guardrail.annotation.SensitiveAction;
import vn.edu.hust.kat.agentservice.service.ActionConfirmationService;
import vn.edu.hust.kat.agentservice.service.AuditService;
import vn.edu.hust.kat.proto.storage.v1.NodeItemResponse;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/**
 * AOP Guardrail & Tool Interceptor:
 * - @ReadAction: Enforces user authentication, executes immediately without confirmation, and audits read description
 * - @SafeAction: Enforces user authentication, captures pre-mutation undo snapshot when supportUndo=true, and audits mutation
 * - @SensitiveAction: Verifies one-time user-approved confirmationToken; blocks with 403 Forbidden if missing or invalid
 */
@Aspect
@Component
@Slf4j
public class GuardrailAspect {

    private static final Gson gson = new Gson();

    private final UserContextManager userContextManager;
    private final AuditService auditService;
    private final ActionConfirmationService actionConfirmationService;
    private final StorageGrpcClient storageGrpcClient;

    @Autowired
    public GuardrailAspect(
            UserContextManager userContextManager,
            AuditService auditService,
            ActionConfirmationService actionConfirmationService,
            @Autowired(required = false) StorageGrpcClient storageGrpcClient
    ) {
        this.userContextManager = userContextManager;
        this.auditService = auditService;
        this.actionConfirmationService = actionConfirmationService;
        this.storageGrpcClient = storageGrpcClient;
    }

    public GuardrailAspect(
            UserContextManager userContextManager,
            AuditService auditService,
            ActionConfirmationService actionConfirmationService
    ) {
        this(userContextManager, auditService, actionConfirmationService, null);
    }

    public GuardrailAspect(UserContextManager userContextManager, AuditService auditService) {
        this(userContextManager, auditService, new ActionConfirmationService(auditService), null);
    }

    @Around("@annotation(readAction)")
    public Object interceptReadAction(ProceedingJoinPoint joinPoint, ReadAction readAction) throws Throwable {
        UUID userId = userContextManager.getCurrentUserId();
        String methodName = joinPoint.getSignature().getName();
        Object[] rawArgs = joinPoint.getArgs();
        String argsStr = Arrays.toString(rawArgs);
        String targetNodeId = extractReadTargetNodeId(methodName, rawArgs);
        String actionType = (readAction.actionType() != null && !readAction.actionType().isBlank())
                ? readAction.actionType()
                : "READ";
        String inputPayload = (readAction.description() != null && !readAction.description().isBlank())
                ? readAction.description() + " | args=" + argsStr
                : argsStr;

        log.debug("[GUARDRAIL - READ] User {} executing read action {} ({}): args={}",
                userId, methodName, readAction.description(), argsStr);

        try {
            Object result = joinPoint.proceed();
            auditService.recordToolAction(
                    userId,
                    actionType,
                    methodName,
                    targetNodeId,
                    inputPayload,
                    null,
                    "SUCCESS"
            );
            return result;
        } catch (Exception e) {
            auditService.recordToolAction(
                    userId,
                    actionType,
                    methodName,
                    targetNodeId,
                    inputPayload + " | error=" + e.getMessage(),
                    null,
                    "FAILED"
            );
            throw e;
        }
    }

    @Around("@annotation(safeAction)")
    public Object interceptSafeAction(ProceedingJoinPoint joinPoint, SafeAction safeAction) throws Throwable {
        UUID userId = userContextManager.getCurrentUserId();
        String methodName = joinPoint.getSignature().getName();
        Object[] rawArgs = joinPoint.getArgs();
        String argsStr = Arrays.toString(rawArgs);
        String targetNodeId = extractSafeTargetNodeId(safeAction.actionType(), rawArgs);

        log.info("[GUARDRAIL - SAFE] User {} executing safe action {} (supportUndo={}): args={}",
                userId, safeAction.actionType(), safeAction.supportUndo(), argsStr);

        String preStateSnapshot = null;
        if (safeAction.supportUndo()) {
            preStateSnapshot = captureUndoSnapshotBeforeExecution(userId, safeAction.actionType(), targetNodeId);
        }

        try {
            Object result = joinPoint.proceed();
            String undoPayload = null;
            if (safeAction.supportUndo()) {
                undoPayload = preStateSnapshot != null
                        ? preStateSnapshot
                        : buildPostExecutionUndoPayload(safeAction.actionType(), targetNodeId, result);
            }

            auditService.recordToolAction(
                    userId,
                    safeAction.actionType(),
                    methodName,
                    targetNodeId,
                    argsStr,
                    undoPayload,
                    "SUCCESS"
            );
            return result;
        } catch (Exception e) {
            auditService.recordToolAction(
                    userId,
                    safeAction.actionType(),
                    methodName,
                    targetNodeId,
                    argsStr + " | error=" + e.getMessage(),
                    null,
                    "FAILED"
            );
            throw e;
        }
    }

    @Around("@annotation(sensitiveAction)")
    public Object interceptSensitiveAction(ProceedingJoinPoint joinPoint, SensitiveAction sensitiveAction) throws Throwable {
        UUID userId = userContextManager.getCurrentUserId();
        String methodName = joinPoint.getSignature().getName();
        Object[] rawArgs = joinPoint.getArgs();
        String argsStr = Arrays.toString(rawArgs);

        String targetNodeId = rawArgs.length > 0 && rawArgs[0] != null ? rawArgs[0].toString().trim() : null;
        String passedToken = rawArgs.length > 1 && rawArgs[1] != null ? rawArgs[1].toString().trim() : null;
        if (passedToken == null || passedToken.isBlank()) {
            passedToken = userContextManager.getCurrentConfirmationToken();
        }

        if (sensitiveAction.requireConfirmationToken()) {
            ActionConfirmationService.TokenValidationResult validation =
                    actionConfirmationService.validateAndConsumeToken(
                            userId,
                            sensitiveAction.actionType(),
                            targetNodeId,
                            passedToken
                    );

            if (!validation.isValid()) {
                log.warn("[GUARDRAIL - SENSITIVE 403 FORBIDDEN] User {} blocked on tool {} (action={}, node={}): {}",
                        userId, methodName, sensitiveAction.actionType(), targetNodeId, validation.getReason());

                auditService.recordToolAction(
                        userId,
                        sensitiveAction.actionType(),
                        methodName,
                        targetNodeId,
                        argsStr,
                        null,
                        "BLOCKED_403_FORBIDDEN"
                );

                return gson.toJson(Map.of(
                        "code", 403,
                        "status", "FORBIDDEN",
                        "action", sensitiveAction.actionType(),
                        "targetNodeId", targetNodeId != null ? targetNodeId : "",
                        "message", "403 Forbidden: " + validation.getReason()
                ));
            }
        }

        log.info("[GUARDRAIL - SENSITIVE APPROVED] User {} executing sensitive action {} on node {}",
                userId, sensitiveAction.actionType(), targetNodeId);

        try {
            Object result = joinPoint.proceed();
            auditService.recordToolAction(
                    userId,
                    sensitiveAction.actionType(),
                    methodName,
                    targetNodeId,
                    argsStr,
                    null,
                    "SUCCESS"
            );
            return result;
        } catch (Exception e) {
            auditService.recordToolAction(
                    userId,
                    sensitiveAction.actionType(),
                    methodName,
                    targetNodeId,
                    argsStr + " | error=" + e.getMessage(),
                    null,
                    "FAILED"
            );
            throw e;
        }
    }

    private String extractReadTargetNodeId(String methodName, Object[] rawArgs) {
        if ("readFileContent".equals(methodName) && rawArgs.length > 0 && rawArgs[0] != null) {
            return rawArgs[0].toString().trim();
        }
        return null;
    }

    private String extractSafeTargetNodeId(String actionType, Object[] rawArgs) {
        if (rawArgs == null || rawArgs.length == 0) {
            return null;
        }
        return switch (actionType) {
            case "RENAME", "MOVE", "SOFT_DELETE", "STAR" ->
                    rawArgs[0] != null ? rawArgs[0].toString().trim() : null;
            case "CREATE_FOLDER" ->
                    rawArgs.length > 1 && rawArgs[1] != null && !rawArgs[1].toString().isBlank()
                            ? rawArgs[1].toString().trim()
                            : "root";
            default -> null;
        };
    }

    private String captureUndoSnapshotBeforeExecution(UUID userId, String actionType, String targetNodeId) {
        if (storageGrpcClient == null || targetNodeId == null || targetNodeId.isBlank() || "CREATE_FOLDER".equals(actionType)) {
            return null;
        }
        try {
            NodeItemResponse node = storageGrpcClient.getNodeMetadata(userId, targetNodeId);
            if (node != null) {
                return gson.toJson(Map.of(
                        "supportUndo", true,
                        "actionType", actionType,
                        "nodeId", node.getId(),
                        "previousName", node.getName(),
                        "previousParentId", node.getParentId(),
                        "previousStarred", node.getIsStarred(),
                        "previousStatus", node.getNodeStatus()
                ));
            }
        } catch (Exception e) {
            log.debug("Could not fetch pre-mutation node snapshot for undoPayload (node={}): {}", targetNodeId, e.getMessage());
        }
        return null;
    }

    private String buildPostExecutionUndoPayload(String actionType, String targetNodeId, Object result) {
        return gson.toJson(Map.of(
                "supportUndo", true,
                "actionType", actionType,
                "targetNodeId", targetNodeId != null ? targetNodeId : "",
                "result", result != null ? result.toString() : ""
        ));
    }
}
