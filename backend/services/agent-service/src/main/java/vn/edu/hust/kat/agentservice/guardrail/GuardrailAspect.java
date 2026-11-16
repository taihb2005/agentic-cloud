package vn.edu.hust.kat.agentservice.guardrail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import vn.edu.hust.kat.agentservice.context.UserContextManager;
import vn.edu.hust.kat.agentservice.guardrail.annotation.ReadAction;
import vn.edu.hust.kat.agentservice.guardrail.annotation.SafeAction;
import vn.edu.hust.kat.agentservice.guardrail.annotation.SensitiveAction;
import vn.edu.hust.kat.agentservice.service.AuditService;

import java.util.Arrays;
import java.util.UUID;

/**
 * AOP Guardrail & Tool Interceptor:
 * - Enforces safety policies before tool execution
 * - Prevents sensitive operations without explicit human confirmation
 * - Records all mutations to AuditDB
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class GuardrailAspect {

    private final UserContextManager userContextManager;
    private final AuditService auditService;

    @Around("@annotation(safeAction)")
    public Object interceptSafeAction(ProceedingJoinPoint joinPoint, SafeAction safeAction) throws Throwable {
        UUID userId = userContextManager.getCurrentUserId();
        String methodName = joinPoint.getSignature().getName();
        String args = Arrays.toString(joinPoint.getArgs());

        log.info("[GUARDRAIL - SAFE] User {} executing safe action {}: args={}", userId, safeAction.actionType(), args);

        try {
            Object result = joinPoint.proceed();
            auditService.recordToolAction(
                    userId,
                    safeAction.actionType(),
                    methodName,
                    null,
                    args,
                    result != null ? result.toString() : null,
                    "SUCCESS"
            );
            return result;
        } catch (Exception e) {
            auditService.recordToolAction(
                    userId,
                    safeAction.actionType(),
                    methodName,
                    null,
                    args,
                    null,
                    "FAILED: " + e.getMessage()
            );
            throw e;
        }
    }

    @Around("@annotation(sensitiveAction)")
    public Object interceptSensitiveAction(ProceedingJoinPoint joinPoint, SensitiveAction sensitiveAction) throws Throwable {
        UUID userId = userContextManager.getCurrentUserId();
        String methodName = joinPoint.getSignature().getName();
        String args = Arrays.toString(joinPoint.getArgs());

        log.warn("[GUARDRAIL - SENSITIVE BLOCKED] User {} attempted sensitive action {} on tool {}",
                userId, sensitiveAction.actionType(), methodName);

        // Record the attempt into AuditDB
        auditService.recordToolAction(
                userId,
                sensitiveAction.actionType(),
                methodName,
                null,
                args,
                null,
                "BLOCKED_REQUIRES_CONFIRMATION"
        );

        // Do not execute destructive code directly without explicit HITL token
        return String.format(
                "{\"status\":\"REQUIRES_CONFIRMATION\",\"action\":\"%s\",\"message\":\"Thao tác này là nhạy cảm. Hệ thống đã tạo đề xuất xác nhận, vui lòng xác nhận trên giao diện để thực thi.\"}",
                sensitiveAction.actionType()
        );
    }

    @Around("@annotation(readAction)")
    public Object interceptReadAction(ProceedingJoinPoint joinPoint, ReadAction readAction) throws Throwable {
        log.debug("[GUARDRAIL - READ] Executing read-only action: {}", joinPoint.getSignature().getName());
        return joinPoint.proceed();
    }
}
