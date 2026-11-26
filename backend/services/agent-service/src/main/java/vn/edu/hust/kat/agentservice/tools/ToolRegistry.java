package vn.edu.hust.kat.agentservice.tools;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import vn.edu.hust.kat.agentservice.config.ToolPromptRegistry;
import vn.edu.hust.kat.agentservice.context.ChatTurnContext;
import vn.edu.hust.kat.agentservice.context.UserContextManager;
import vn.edu.hust.kat.agentservice.security.UserPrincipal;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Getter
@Slf4j
public class ToolRegistry {

    private final StorageReadTools readTools;
    private final StorageSafeTools safeTools;
    private final StorageSensitiveTools sensitiveTools;
    private final UserMemoryTools userMemoryTools;
    private final ToolPromptRegistry toolPromptRegistry;
    private final UserContextManager userContextManager;

    @Autowired
    public ToolRegistry(
            StorageReadTools readTools,
            StorageSafeTools safeTools,
            StorageSensitiveTools sensitiveTools,
            UserMemoryTools userMemoryTools,
            ToolPromptRegistry toolPromptRegistry,
            UserContextManager userContextManager
    ) {
        this.readTools = readTools;
        this.safeTools = safeTools;
        this.sensitiveTools = sensitiveTools;
        this.userMemoryTools = userMemoryTools;
        this.toolPromptRegistry = toolPromptRegistry;
        this.userContextManager = userContextManager;
    }

    public ToolRegistry(
            StorageReadTools readTools,
            StorageSafeTools safeTools,
            StorageSensitiveTools sensitiveTools,
            UserMemoryTools userMemoryTools,
            ToolPromptRegistry toolPromptRegistry
    ) {
        this(readTools, safeTools, sensitiveTools, userMemoryTools, toolPromptRegistry, new UserContextManager());
    }

    public ITools[] getAllTools() {
        return new ITools[]{readTools, safeTools, sensitiveTools, userMemoryTools};
    }

    /**
     * Builds a LangChain4j {@link ToolProvider} that dynamically supplies all registered Spring bean tools
     * and wraps every {@link ToolExecutor} with {@link SecurityContext} so tools and {@code GuardrailAspect}
     * can always identify which user is invoking the current tool.
     */
    public ToolProvider getToolProvider() {
        Map<ToolSpecification, ToolExecutor> baseToolExecutors = discoverBaseToolExecutors();
        return request -> provideTools(request, baseToolExecutors);
    }

    private ToolProviderResult provideTools(
            ToolProviderRequest request,
            Map<ToolSpecification, ToolExecutor> baseToolExecutors
    ) {
        SecurityContext callerContext = SecurityContextHolder.getContext();
        Authentication capturedAuth = callerContext != null ? callerContext.getAuthentication() : null;
        ChatTurnContext capturedTurnContext = userContextManager.getCurrentTurnContext();
        String capturedConfirmationToken = userContextManager.getCurrentConfirmationToken();
        Object providerMemoryId = request != null ? request.chatMemoryId() : null;

        ToolProviderResult.Builder resultBuilder = ToolProviderResult.builder();
        for (Map.Entry<ToolSpecification, ToolExecutor> entry : baseToolExecutors.entrySet()) {
            ToolExecutor contextAwareExecutor = wrapWithExecutionContext(
                    entry.getValue(),
                    providerMemoryId,
                    capturedAuth,
                    capturedTurnContext,
                    capturedConfirmationToken
            );
            resultBuilder.add(entry.getKey(), contextAwareExecutor);
        }
        return resultBuilder.build();
    }

    /**
     * Unwraps Spring AOP CGLIB proxies when scanning {@link Tool} metadata so LangChain4j
     * discovers every {@code @Tool} method, enriches descriptions from {@link ToolPromptRegistry}
     * (loaded from external YAML), while keeping the proxy bean as the invocation target
     * so {@code GuardrailAspect} still intercepts every tool call.
     */
    public Map<ToolSpecification, ToolExecutor> getToolExecutors() {
        SecurityContext callerContext = SecurityContextHolder.getContext();
        Authentication capturedAuth = callerContext != null ? callerContext.getAuthentication() : null;
        ChatTurnContext capturedTurnContext = userContextManager.getCurrentTurnContext();
        String capturedConfirmationToken = userContextManager.getCurrentConfirmationToken();

        Map<ToolSpecification, ToolExecutor> baseToolExecutors = discoverBaseToolExecutors();
        Map<ToolSpecification, ToolExecutor> toolExecutors = new LinkedHashMap<>();
        for (Map.Entry<ToolSpecification, ToolExecutor> entry : baseToolExecutors.entrySet()) {
            toolExecutors.put(
                    entry.getKey(),
                    wrapWithExecutionContext(
                            entry.getValue(),
                            null,
                            capturedAuth,
                            capturedTurnContext,
                            capturedConfirmationToken
                    )
            );
        }
        return toolExecutors;
    }

    private Map<ToolSpecification, ToolExecutor> discoverBaseToolExecutors() {
        Map<ToolSpecification, ToolExecutor> baseExecutors = new LinkedHashMap<>();
        List<String> registeredNames = new ArrayList<>();

        for (ITools toolBean : getAllTools()) {
            Class<?> targetClass = AopUtils.getTargetClass(toolBean);
            for (Method method : targetClass.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Tool.class)) {
                    continue;
                }
                ToolSpecification baseSpec = ToolSpecifications.toolSpecificationFrom(method);
                ToolSpecification specification = toolPromptRegistry.enrich(baseSpec);

                ToolExecutor delegate = new DefaultToolExecutor(toolBean, method);
                baseExecutors.put(specification, delegate);
                registeredNames.add(specification.name());
            }
        }

        ToolSpecifications.validateSpecifications(new ArrayList<>(baseExecutors.keySet()));
        log.info("Registered {} LangChain4j tools with external prompt definitions: {}", registeredNames.size(), registeredNames);
        return baseExecutors;
    }

    private ToolExecutor wrapWithExecutionContext(
            ToolExecutor delegate,
            Object providerMemoryId,
            Authentication capturedAuth,
            ChatTurnContext capturedTurnContext,
            String capturedConfirmationToken
    ) {
        return (request, memoryId) -> {
            Object effectiveMemoryId = memoryId != null ? memoryId : providerMemoryId;

            SecurityContext previousSecurityContext = SecurityContextHolder.getContext();
            Authentication currentAuth = previousSecurityContext != null ? previousSecurityContext.getAuthentication() : null;
            boolean restoredAuth = false;
            if ((currentAuth == null || !(currentAuth.getPrincipal() instanceof UserPrincipal))
                    && capturedAuth != null && capturedAuth.getPrincipal() instanceof UserPrincipal) {
                SecurityContext tempContext = SecurityContextHolder.createEmptyContext();
                tempContext.setAuthentication(capturedAuth);
                SecurityContextHolder.setContext(tempContext);
                currentAuth = capturedAuth;
                restoredAuth = true;
            } else if ((currentAuth == null || !(currentAuth.getPrincipal() instanceof UserPrincipal))
                    && effectiveMemoryId != null) {
                Authentication memoryAuth = buildAuthFromMemoryId(effectiveMemoryId.toString());
                if (memoryAuth != null) {
                    SecurityContext tempContext = SecurityContextHolder.createEmptyContext();
                    tempContext.setAuthentication(memoryAuth);
                    SecurityContextHolder.setContext(tempContext);
                    currentAuth = memoryAuth;
                    restoredAuth = true;
                }
            }

            if (currentAuth == null || !(currentAuth.getPrincipal() instanceof UserPrincipal)) {
                log.warn("Missing or invalid UserPrincipal in SecurityContext when executing tool '{}'", request.name());
                throw new AccessDeniedException("Người dùng không hợp lệ hoặc chưa đăng nhập");
            }

            ChatTurnContext previousTurnContext = userContextManager.getCurrentTurnContext();
            boolean restoredTurnContext = false;
            ChatTurnContext effectiveTurnContext = previousTurnContext;
            if (effectiveTurnContext == null && capturedTurnContext != null) {
                userContextManager.setCurrentTurnContext(capturedTurnContext);
                effectiveTurnContext = capturedTurnContext;
                restoredTurnContext = true;
            }

            String previousConfirmationToken = userContextManager.getCurrentConfirmationToken();
            boolean restoredConfirmationToken = false;
            if ((previousConfirmationToken == null || previousConfirmationToken.isBlank())
                    && capturedConfirmationToken != null && !capturedConfirmationToken.isBlank()) {
                userContextManager.setCurrentConfirmationToken(capturedConfirmationToken);
                restoredConfirmationToken = true;
            }

            String previousToolCallId = null;
            if (effectiveTurnContext != null) {
                previousToolCallId = effectiveTurnContext.getCurrentToolCallId();
                effectiveTurnContext.setCurrentToolCallId(request.id());
                effectiveTurnContext.clearLastToolArtifacts();
            }

            try {
                UUID currentUserId = ((UserPrincipal) currentAuth.getPrincipal()).getUserId();
                log.info("Executing tool '{}' (toolCallId={}) for user={} (memoryId={})",
                        request.name(), request.id(), currentUserId, effectiveMemoryId);
                return delegate.execute(request, effectiveMemoryId);
            } finally {
                if (effectiveTurnContext != null) {
                    effectiveTurnContext.setCurrentToolCallId(previousToolCallId);
                    effectiveTurnContext.clearLastToolArtifacts();
                }
                if (restoredConfirmationToken) {
                    userContextManager.setCurrentConfirmationToken(previousConfirmationToken);
                }
                if (restoredTurnContext) {
                    userContextManager.setCurrentTurnContext(previousTurnContext);
                }
                if (restoredAuth) {
                    if (previousSecurityContext != null && previousSecurityContext.getAuthentication() != null) {
                        SecurityContextHolder.setContext(previousSecurityContext);
                    } else {
                        SecurityContextHolder.clearContext();
                    }
                }
            }
        };
    }

    private Authentication buildAuthFromMemoryId(String memoryId) {
        if (memoryId == null || memoryId.isBlank()) {
            return null;
        }
        int colonIdx = memoryId.indexOf(':');
        String userIdPart = colonIdx > 0 ? memoryId.substring(0, colonIdx).trim() : memoryId.trim();
        try {
            UUID userId = UUID.fromString(userIdPart);
            UserPrincipal principal = new UserPrincipal(userId, Collections.emptyList());
            return new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
