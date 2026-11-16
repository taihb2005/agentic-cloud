package vn.edu.hust.kat.agentservice.guardrail.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a tool action as sensitive/dangerous (hard delete, public share, permissions grant).
 * Requires explicit Human-in-the-Loop (HITL) approval before execution.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SensitiveAction {
    String actionType();
}
