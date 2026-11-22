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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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
        Authentication capturedAuth = callerContext.getAuthentication();

        ToolProviderResult.Builder resultBuilder = ToolProviderResult.builder();
        for (Map.Entry<ToolSpecification, ToolExecutor> entry : baseToolExecutors.entrySet()) {
            ToolExecutor contextAwareExecutor = wrapWithSecurityContext(entry.getValue(), capturedAuth);
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
        Authentication capturedAuth = callerContext.getAuthentication();

        Map<ToolSpecification, ToolExecutor> baseToolExecutors = discoverBaseToolExecutors();
        Map<ToolSpecification, ToolExecutor> toolExecutors = new LinkedHashMap<>();
        for (Map.Entry<ToolSpecification, ToolExecutor> entry : baseToolExecutors.entrySet()) {
            toolExecutors.put(
                    entry.getKey(),
                    wrapWithSecurityContext(entry.getValue(), capturedAuth)
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

    private ToolExecutor wrapWithSecurityContext(
            ToolExecutor delegate,
            Object providerMemoryId
    ) {
        return (request, memoryId) -> {
            Object effectiveMemoryId = memoryId != null ? memoryId : providerMemoryId;

            Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();

            if (currentAuth == null || !(currentAuth.getPrincipal() instanceof UserPrincipal)) {
                log.warn("Missing or invalid UserPrincipal in SecurityContext when executing tool '{}'", request.name());
                throw new AccessDeniedException("Người dùng không hơp lệ hoặc chưa đăng nhập");
            }

            UUID currentUserId = ((UserPrincipal) currentAuth.getPrincipal()).getUserId();
            log.info("Executing tool '{}' for user={} (memoryId={})", request.name(), currentUserId, effectiveMemoryId);

            return delegate.execute(request, effectiveMemoryId);
        };
    }
}
