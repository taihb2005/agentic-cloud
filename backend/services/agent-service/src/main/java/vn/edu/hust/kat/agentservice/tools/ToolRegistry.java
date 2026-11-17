package vn.edu.hust.kat.agentservice.tools;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import vn.edu.hust.kat.agentservice.config.ToolPromptRegistry;
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
@RequiredArgsConstructor
@Slf4j
public class ToolRegistry {

    private final StorageReadTools readTools;
    private final StorageSafeTools safeTools;
    private final StorageSensitiveTools sensitiveTools;
    private final UserMemoryTools userMemoryTools;
    private final ToolPromptRegistry toolPromptRegistry;

    public Object[] getAllTools() {
        return new Object[]{readTools, safeTools, sensitiveTools, userMemoryTools};
    }

    /**
     * Unwraps Spring AOP CGLIB proxies when scanning {@link Tool} metadata so LangChain4j
     * discovers every {@code @Tool} method, enriches descriptions from {@link ToolPromptRegistry}
     * (loaded from external YAML), while keeping the proxy bean as the invocation target
     * so {@code GuardrailAspect} still intercepts every tool call.
     */
    public Map<ToolSpecification, ToolExecutor> getToolExecutors() {
        // Map ToolSpecs to ToolExecutors
        Map<ToolSpecification, ToolExecutor> toolExecutors = new LinkedHashMap<>();
        List<String> registeredNames = new ArrayList<>();

        for (Object toolBean : getAllTools()) {
            Class<?> targetClass = AopUtils.getTargetClass(toolBean);
            for (Method method : targetClass.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Tool.class)) {
                    continue;
                }
                ToolSpecification baseSpec = ToolSpecifications.toolSpecificationFrom(method);
                // Enrich params to the tools
                ToolSpecification specification = toolPromptRegistry.enrich(baseSpec);

                // Wrap the executor with a security aspect to ensure work only with this specific user id
                ToolExecutor delegate = new DefaultToolExecutor(toolBean, method);
                ToolExecutor contextAwareExecutor = wrapWithSecurityContext(delegate);
                toolExecutors.put(specification, contextAwareExecutor);
                registeredNames.add(specification.name());
            }
        }

        //Validate dynamic tool to Langchain
        ToolSpecifications.validateSpecifications(new ArrayList<>(toolExecutors.keySet()));
        log.info("Registered {} LangChain4j tools with external prompt definitions: {}", registeredNames.size(), registeredNames);
        return toolExecutors;
    }

    private ToolExecutor wrapWithSecurityContext(ToolExecutor delegate) {
        return (request, memoryId) -> {
            SecurityContext previousContext = SecurityContextHolder.getContext();
            boolean populatedTempContext = false;

            if ((previousContext == null || previousContext.getAuthentication() == null) && memoryId != null) {
                //memoryId=userId+ ':' + conversationId
                UUID userId = parseUserIdFromMemoryId(memoryId);
                if (userId != null) {
                    UserPrincipal principal = UserPrincipal.builder()
                            .userId(userId)
                            .authorities(Collections.emptyList())
                            .build();
                    SecurityContext tempContext = SecurityContextHolder.createEmptyContext();
                    tempContext.setAuthentication(
                            new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList())
                    );
                    SecurityContextHolder.setContext(tempContext);
                    populatedTempContext = true;
                }
            }

            try {
                log.info("Executing tool '{}' for memoryId={}", request.name(), memoryId);
                return delegate.execute(request, memoryId);
            } finally {
                if (populatedTempContext) {
                    SecurityContextHolder.clearContext();
                    if (previousContext != null) {
                        SecurityContextHolder.setContext(previousContext);
                    }
                }
            }
        };
    }

    private UUID parseUserIdFromMemoryId(Object memoryId) {
        String raw = memoryId.toString();
        int colonIdx = raw.indexOf(':');
        String userIdPart = colonIdx > 0 ? raw.substring(0, colonIdx) : raw;
        try {
            return UUID.fromString(userIdPart);
        } catch (IllegalArgumentException e) {
            log.warn("Could not parse UUID from memoryId '{}' while binding SecurityContext for tool execution", raw);
            return null;
        }
    }
}
