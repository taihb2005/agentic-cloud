package vn.edu.hust.kat.agentservice.guardrail.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a tool action as read-only (100% safe, no state modification).
 * Executes directly for the authenticated user without requiring confirmation.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ReadAction {
    String actionType() default "READ";
    String description() default "";
}
