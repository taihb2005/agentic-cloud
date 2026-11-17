package vn.edu.hust.kat.agentservice.tools;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.edu.hust.kat.agentservice.config.ToolPromptRegistry;
import vn.edu.hust.kat.agentservice.context.UserContextManager;
import vn.edu.hust.kat.agentservice.grpc.StorageGrpcClient;
import vn.edu.hust.kat.agentservice.guardrail.GuardrailAspect;
import vn.edu.hust.kat.agentservice.memory.DualLayerMemoryManager;
import vn.edu.hust.kat.agentservice.service.ActionConfirmationService;
import vn.edu.hust.kat.agentservice.service.AuditService;

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
        ActionConfirmationService actionConfirmationService = new ActionConfirmationService(auditService);
        GuardrailAspect guardrailAspect = new GuardrailAspect(
                userContextManager,
                auditService,
                actionConfirmationService,
                storageGrpcClient
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
        // updateUserPreference has supportUndo = false, so undoPayload is null
        verify(auditService).recordToolAction(
                eq(userId),
                eq("UPDATE_USER_PREF"),
                eq("updateUserPreference"),
                isNull(),
                anyString(),
                isNull(),
                eq("SUCCESS")
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
