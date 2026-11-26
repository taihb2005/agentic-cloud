package vn.edu.hust.kat.agentservice.context;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import vn.edu.hust.kat.agentservice.security.UserPrincipal;

import java.util.UUID;

/**
 * Context Manager: Extracts User Session, maintains request context,
 * and constructs Dynamic System Prompts for the AI Agent.
 */
@Component
@Slf4j
public class UserContextManager {

    private static final ThreadLocal<String> CONFIRMATION_TOKEN_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<ChatTurnContext> TURN_CONTEXT_HOLDER = new ThreadLocal<>();

    public UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getUserId();
        }
        throw new IllegalStateException("No authenticated user found in SecurityContext");
    }

    public String getCurrentUserIdAsString() {
        UUID userId = getCurrentUserId();
        return userId != null ? userId.toString() : "anonymous";
    }

    public void setCurrentConfirmationToken(String confirmationToken) {
        if (confirmationToken != null && !confirmationToken.isBlank()) {
            CONFIRMATION_TOKEN_HOLDER.set(confirmationToken.trim());
        } else {
            CONFIRMATION_TOKEN_HOLDER.remove();
        }
    }

    public String getCurrentConfirmationToken() {
        return CONFIRMATION_TOKEN_HOLDER.get();
    }

    public void clearCurrentConfirmationToken() {
        CONFIRMATION_TOKEN_HOLDER.remove();
    }

    public void setCurrentTurnContext(ChatTurnContext turnContext) {
        if (turnContext != null) {
            TURN_CONTEXT_HOLDER.set(turnContext);
        } else {
            TURN_CONTEXT_HOLDER.remove();
        }
    }

    public ChatTurnContext getCurrentTurnContext() {
        return TURN_CONTEXT_HOLDER.get();
    }

    public void clearCurrentTurnContext() {
        TURN_CONTEXT_HOLDER.remove();
    }
}
