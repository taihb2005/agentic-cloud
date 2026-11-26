package vn.edu.hust.kat.agentservice.tools;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.edu.hust.kat.agentservice.config.ToolPromptRegistry;
import vn.edu.hust.kat.agentservice.context.UserContextManager;
import vn.edu.hust.kat.agentservice.entity.AgentAuditLog;
import vn.edu.hust.kat.agentservice.grpc.StorageGrpcClient;
import vn.edu.hust.kat.agentservice.guardrail.GuardrailAspect;
import vn.edu.hust.kat.agentservice.guardrail.snapshot.UndoSnapshotCaptureService;
import vn.edu.hust.kat.agentservice.memory.DualLayerMemoryManager;
import vn.edu.hust.kat.agentservice.security.UserPrincipal;
import vn.edu.hust.kat.agentservice.service.ActionConfirmationService;
import vn.edu.hust.kat.agentservice.service.AuditService;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ToolRegistryTest {

    @Mock
    private DualLayerMemoryManager memoryManager;

    @Mock
    private AuditService auditService;

    @Mock
    private StorageGrpcClient storageGrpcClient;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getToolExecutors_DiscoversAllToolsEvenThroughSpringCglibProxies_AndTriggersGuardrailAspect() {
        UserContextManager userContextManager = new UserContextManager();
        ActionConfirmationService actionConfirmationService = new ActionConfirmationService(auditService, null, null);
        GuardrailAspect guardrailAspect = new GuardrailAspect(
                userContextManager,
                auditService,
                actionConfirmationService,
                new UndoSnapshotCaptureService(
                        JsonMapper.builder()
                                .findAndAddModules()
                                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                                .build()
                )
        );

        StorageReadTools readToolsProxy = wrapWithAspect(
                new StorageReadTools(userContextManager, storageGrpcClient),
                guardrailAspect
        );
        StorageSafeTools safeToolsProxy = wrapWithAspect(
                new StorageSafeTools(userContextManager, storageGrpcClient),
                guardrailAspect
        );
        StorageSensitiveTools sensitiveToolsProxy = wrapWithAspect(
                new StorageSensitiveTools(userContextManager, storageGrpcClient, actionConfirmationService),
                guardrailAspect
        );
        UserMemoryTools memoryToolsProxy = wrapWithAspect(
                new UserMemoryTools(userContextManager, memoryManager),
                guardrailAspect
        );

        // Reproduce the CGLIB proxy issue: LangChain4j's default scanner misses @Tool on proxy subclasses
        assertTrue(ToolSpecifications.toolSpecificationsFrom(memoryToolsProxy).isEmpty());

        ToolPromptRegistry promptRegistry = new ToolPromptRegistry();
        ToolRegistry registry = new ToolRegistry(
                readToolsProxy,
                safeToolsProxy,
                sensitiveToolsProxy,
                memoryToolsProxy,
                promptRegistry
        );
        Map<ToolSpecification, ToolExecutor> executors = registry.getToolExecutors();

        assertEquals(15, executors.size());

        ToolSpecification readFileSpec = executors.keySet().stream()
                .filter(spec -> spec.name().equals("readFileContent"))
                .findFirst()
                .orElseThrow();
        assertEquals(
                promptRegistry.getPromptDefinition("readFileContent").getDescription(),
                readFileSpec.description()
        );
        JsonStringSchema nodeIdSchema = (JsonStringSchema) readFileSpec.parameters().properties().get("nodeId");
        assertNotNull(nodeIdSchema);
        assertEquals(
                promptRegistry.getPromptDefinition("readFileContent").getParameters().get("nodeId"),
                nodeIdSchema.description()
        );

        ToolSpecification updatePrefSpec = executors.keySet().stream()
                .filter(spec -> spec.name().equals("updateUserPreference"))
                .findFirst()
                .orElseThrow();
        assertTrue(updatePrefSpec.description().contains("sở thích/quy tắc cá nhân dài hạn"));

        UUID userId = UUID.randomUUID();
        when(memoryManager.upsertUserPreference(userId.toString(), "mobile_os", "android"))
                .thenReturn("vec-1");

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call-1")
                .name("updateUserPreference")
                .arguments("{\"preferenceKey\":\"mobile_os\",\"preferenceValue\":\"android\"}")
                .build();

        // Simulate async streaming thread where SecurityContextHolder starts empty
        SecurityContextHolder.clearContext();
        String result = executors.get(updatePrefSpec).execute(request, userId + ":session-1");

        assertTrue(result.contains("android"));
        verify(memoryManager).upsertUserPreference(userId.toString(), "mobile_os", "android");
        // updateUserPreference now implements Undoable with supportUndo = true
        verify(auditService).recordToolAction(
                eq(userId),
                eq("UPDATE_USER_PREF"),
                eq("updateUserPreference"),
                isNull(),
                anyString(),
                argThat(payload -> payload != null && payload.contains("\"category\":\"MEMORY\"")),
                eq(AgentAuditLog.Status.SUCCESS),
                eq(AgentAuditLog.HitlStatus.NOT_REQUIRED)
        );
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void toolProvider_WrapsToolExecutionWithSecurityContext_ForCurrentUser() {
        UserContextManager userContextManager = new UserContextManager();
        ActionConfirmationService actionConfirmationService = new ActionConfirmationService(auditService, null, null);
        GuardrailAspect guardrailAspect = new GuardrailAspect(
                userContextManager,
                auditService,
                actionConfirmationService,
                new UndoSnapshotCaptureService(
                        JsonMapper.builder()
                                .findAndAddModules()
                                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                                .build()
                )
        );

        StorageReadTools readToolsProxy = wrapWithAspect(
                new StorageReadTools(userContextManager, storageGrpcClient),
                guardrailAspect
        );
        StorageSafeTools safeToolsProxy = wrapWithAspect(
                new StorageSafeTools(userContextManager, storageGrpcClient),
                guardrailAspect
        );
        StorageSensitiveTools sensitiveToolsProxy = wrapWithAspect(
                new StorageSensitiveTools(userContextManager, storageGrpcClient, actionConfirmationService),
                guardrailAspect
        );
        UserMemoryTools memoryToolsProxy = wrapWithAspect(
                new UserMemoryTools(userContextManager, memoryManager),
                guardrailAspect
        );

        ToolRegistry registry = new ToolRegistry(
                readToolsProxy,
                safeToolsProxy,
                sensitiveToolsProxy,
                memoryToolsProxy,
                new ToolPromptRegistry()
        );

        ToolProvider toolProvider = registry.getToolProvider();
        UUID userId = UUID.randomUUID();

        // Bind user in SecurityContext when ToolProvider is invoked on HTTP request thread
        UserPrincipal principal = UserPrincipal.builder()
                .userId(userId)
                .authorities(Collections.emptyList())
                .build();
        SecurityContext callerContext = SecurityContextHolder.createEmptyContext();
        callerContext.setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList())
        );
        SecurityContextHolder.setContext(callerContext);

        ToolProviderRequest providerRequest = new ToolProviderRequest(
                userId + ":session-stream",
                UserMessage.from("Cập nhật sở thích hệ điều hành của tôi")
        );
        ToolProviderResult providerResult = toolProvider.provideTools(providerRequest);
        assertEquals(15, providerResult.tools().size());

        ToolSpecification updatePrefSpec = providerResult.tools().keySet().stream()
                .filter(spec -> spec.name().equals("updateUserPreference"))
                .findFirst()
                .orElseThrow();

        when(memoryManager.upsertUserPreference(userId.toString(), "mobile_os", "ios"))
                .thenReturn("vec-2");

        ToolExecutionRequest toolExecRequest = ToolExecutionRequest.builder()
                .id("call-2")
                .name("updateUserPreference")
                .arguments("{\"preferenceKey\":\"mobile_os\",\"preferenceValue\":\"ios\"}")
                .build();

        // Simulate async streaming thread where SecurityContextHolder is empty during tool execution
        SecurityContextHolder.clearContext();
        String output = providerResult.tools().get(updatePrefSpec).execute(toolExecRequest, null);

        assertTrue(output.contains("ios"));
        verify(memoryManager).upsertUserPreference(userId.toString(), "mobile_os", "ios");
        verify(auditService).recordToolAction(
                eq(userId),
                eq("UPDATE_USER_PREF"),
                eq("updateUserPreference"),
                isNull(),
                anyString(),
                argThat(payload -> payload != null && payload.contains("\"category\":\"MEMORY\"")),
                eq(AgentAuditLog.Status.SUCCESS),
                eq(AgentAuditLog.HitlStatus.NOT_REQUIRED)
        );
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private <T> T wrapWithAspect(T target, GuardrailAspect aspect) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(aspect);
        return factory.getProxy();
    }
}
