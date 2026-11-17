package vn.edu.hust.kat.agentservice.guardrail.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a tool action as safe and recoverable (rename, move, create folder, soft delete/trash, star).
 * The agent can automatically execute this when instructed by the user, and GuardrailAspect
 * captures pre-mutation state into undo_payload when supportUndo is true.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeAction {
    String actionType();
    boolean supportUndo() default true;
}
