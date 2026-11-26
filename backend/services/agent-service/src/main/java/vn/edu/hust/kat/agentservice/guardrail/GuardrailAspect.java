package vn.edu.hust.kat.agentservice.guardrail;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.edu.hust.kat.agentservice.context.ChatTurnContext;
import vn.edu.hust.kat.agentservice.context.UserContextManager;
import vn.edu.hust.kat.agentservice.entity.AgentAuditLog;
import vn.edu.hust.kat.agentservice.grpc.StorageGrpcClient;
import vn.edu.hust.kat.agentservice.guardrail.annotation.ReadAction;
import vn.edu.hust.kat.agentservice.guardrail.annotation.SafeAction;
import vn.edu.hust.kat.agentservice.guardrail.annotation.SensitiveAction;
import vn.edu.hust.kat.agentservice.guardrail.snapshot.UndoSnapshotCaptureService;
import vn.edu.hust.kat.agentservice.guardrail.snapshot.payload.UndoSnapshotPayload;
import vn.edu.hust.kat.agentservice.service.ActionConfirmationService;
import vn.edu.hust.kat.agentservice.service.AuditService;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/**
 * AOP Guardrail & Tool Interceptor:
 * - @ReadAction: Enforces user authentication, executes immediately without confirmation, and audits read description
 * - @SafeAction: Enforces user authentication, captures pre/post-mutation undo snapshot when supportUndo=true, and audits mutation
 * - @SensitiveAction: Verifies one-time user-approved confirmationToken; blocks with 403 Forbidden if missing or invalid;
 *   captures undo snapshot when supportUndo=true (e.g. PUBLIC_SHARE)
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class GuardrailAspect {

    private static final Gson gson = new Gson();

    private final UserContextManager userContextManager;
    private final AuditService auditService;
    private final ActionConfirmationService actionConfirmationService;
    private final UndoSnapshotCaptureService undoSnapshotCaptureService;

    @Around("@annotation(readAction)")
    public Object interceptReadAction(ProceedingJoinPoint joinPoint, ReadAction readAction) throws Throwable {
        UUID userId = userContextManager.getCurrentUserId();
        ChatTurnContext turnContext = userContextManager.getCurrentTurnContext();

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

        log.debug("[GUARDRAIL] User {} executing read action {} ({}): args={}", userId, methodName, readAction.description(), argsStr);

        try {
            Object result = joinPoint.proceed();
            recordAudit(userId, turnContext, actionType, methodName, targetNodeId, inputPayload, null,
                    AgentAuditLog.Status.SUCCESS, AgentAuditLog.HitlStatus.NOT_REQUIRED, null);
            return result;
        } catch (Exception e) {
            recordAudit(userId, turnContext, actionType, methodName, targetNodeId, inputPayload, null,
                    AgentAuditLog.Status.FAILED, AgentAuditLog.HitlStatus.NOT_REQUIRED, e.getMessage());
            throw e;
        }
    }

    @Around("@annotation(safeAction)")
    public Object interceptSafeAction(ProceedingJoinPoint joinPoint, SafeAction safeAction) throws Throwable {
        UUID userId = userContextManager.getCurrentUserId();
        ChatTurnContext turnContext = userContextManager.getCurrentTurnContext();

        String methodName = joinPoint.getSignature().getName();
        Object[] rawArgs = joinPoint.getArgs();
        String argsStr = Arrays.toString(rawArgs);
        String targetNodeId = extractSafeTargetNodeId(safeAction.actionType(), rawArgs);

        log.info("[GUARDRAIL] User {} executing safe action {} (supportUndo={}): args={}", userId, safeAction.actionType(), safeAction.supportUndo(), argsStr);

        Object targetBean = joinPoint.getTarget();
        UndoSnapshotPayload preSnapshot = null;
        if (safeAction.supportUndo()) {
            preSnapshot = undoSnapshotCaptureService.capturePreState(
                    targetBean, userId, safeAction.actionType(), targetNodeId, rawArgs
            );
        }

        try {
            Object result = joinPoint.proceed();
            String undoPayload = null;
            if (safeAction.supportUndo()) {
                undoPayload = undoSnapshotCaptureService.finalizeAndSerialize(
                        targetBean,
                        userId,
                        safeAction.actionType(),
                        preSnapshot,
                        result,
                        turnContext
                );
            }

            String effectiveTargetNodeId = resolveFinalTargetNodeId(safeAction.actionType(), targetNodeId, turnContext);
            recordAudit(userId, turnContext, safeAction.actionType(), methodName, effectiveTargetNodeId, argsStr,
                    undoPayload, AgentAuditLog.Status.SUCCESS, AgentAuditLog.HitlStatus.NOT_REQUIRED, null);
            return result;
        } catch (Exception e) {
            recordAudit(userId, turnContext, safeAction.actionType(), methodName, targetNodeId, argsStr,
                    null, AgentAuditLog.Status.FAILED, AgentAuditLog.HitlStatus.NOT_REQUIRED, e.getMessage());
            throw e;
        }
    }

    @Around("@annotation(sensitiveAction)")
    public Object interceptSensitiveAction(ProceedingJoinPoint joinPoint, SensitiveAction sensitiveAction) throws Throwable {
        UUID userId = userContextManager.getCurrentUserId();
        ChatTurnContext turnContext = userContextManager.getCurrentTurnContext();

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
                log.warn("[GUARDRAIL] User {} blocked on tool {} (action={}, node={}): {}",
                        userId, methodName, sensitiveAction.actionType(), targetNodeId, validation.getReason());

                recordAudit(userId, turnContext, sensitiveAction.actionType(), methodName, targetNodeId, argsStr,
                        null, AgentAuditLog.Status.BLOCKED, AgentAuditLog.HitlStatus.REQUIRED, validation.getReason());

                return gson.toJson(Map.of(
                        "code", 403,
                        "status", "FORBIDDEN",
                        "action", sensitiveAction.actionType(),
                        "targetNodeId", targetNodeId != null ? targetNodeId : "",
                        "message", "403 Forbidden: " + validation.getReason()
                ));
            }
        }

        AgentAuditLog.HitlStatus hitlStatus = sensitiveAction.requireConfirmationToken()
                ? AgentAuditLog.HitlStatus.APPROVED
                : AgentAuditLog.HitlStatus.NOT_REQUIRED;

        Object targetBean = joinPoint.getTarget();
        UndoSnapshotPayload preSnapshot = null;
        if (sensitiveAction.supportUndo() && undoSnapshotCaptureService != null) {
            preSnapshot = undoSnapshotCaptureService.capturePreState(
                    targetBean, userId, sensitiveAction.actionType(), targetNodeId, rawArgs
            );
        }

        log.info("[GUARDRAIL - SENSITIVE APPROVED] User {} executing sensitive action {} on node {} (supportUndo={})",
                userId, sensitiveAction.actionType(), targetNodeId, sensitiveAction.supportUndo());

        try {
            Object result = joinPoint.proceed();
            String undoPayload = null;
            if (sensitiveAction.supportUndo() && undoSnapshotCaptureService != null) {
                undoPayload = undoSnapshotCaptureService.finalizeAndSerialize(
                        targetBean,
                        userId,
                        sensitiveAction.actionType(),
                        preSnapshot,
                        result,
                        turnContext
                );
            }

            recordAudit(userId, turnContext, sensitiveAction.actionType(), methodName, targetNodeId, argsStr,
                    undoPayload, AgentAuditLog.Status.SUCCESS, hitlStatus, null);
            return result;
        } catch (Exception e) {
            recordAudit(userId, turnContext, sensitiveAction.actionType(), methodName, targetNodeId, argsStr,
                    null, AgentAuditLog.Status.FAILED, hitlStatus, e.getMessage());
            throw e;
        }
    }

    private void recordAudit(
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
        if (turnContext != null) {
            auditService.recordToolAction(
                    userId,
                    turnContext,
                    actionType,
                    toolName,
                    targetNodeId,
                    inputPayload,
                    undoPayload,
                    status,
                    hitlStatus,
                    reason
            );
        } else if (reason != null) {
            auditService.recordToolAction(
                    userId,
                    actionType,
                    toolName,
                    targetNodeId,
                    inputPayload,
                    undoPayload,
                    status,
                    hitlStatus,
                    reason
            );
        } else {
            auditService.recordToolAction(
                    userId,
                    actionType,
                    toolName,
                    targetNodeId,
                    inputPayload,
                    undoPayload,
                    status,
                    hitlStatus
            );
        }
    }

    private String resolveFinalTargetNodeId(String actionType, String preTargetNodeId, ChatTurnContext turnContext) {
        if ("CREATE_FOLDER".equals(actionType)
                && turnContext != null
                && turnContext.getLastCreatedResourceId() != null
                && !turnContext.getLastCreatedResourceId().isBlank()) {
            return turnContext.getLastCreatedResourceId();
        }
        return preTargetNodeId;
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
}
