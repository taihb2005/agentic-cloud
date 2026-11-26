package vn.edu.hust.kat.agentservice.guardrail.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a tool action as sensitive/dangerous (hard delete, public share).
 * Requires an explicit Human-in-the-Loop (HITL) confirmationToken approved by the user;
 * otherwise GuardrailAspect blocks execution and returns 403 Forbidden.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SensitiveAction {
    String actionType();
    boolean requireConfirmationToken() default true;
    boolean supportUndo() default false;
}
