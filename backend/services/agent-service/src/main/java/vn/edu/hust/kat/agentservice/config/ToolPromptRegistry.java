package vn.edu.hust.kat.agentservice.config;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads @Tool and @P descriptions from an external YAML file (default: classpath:prompts/tool-prompts.yml)
 * and enriches LangChain4j {@link ToolSpecification} instances at runtime without hardcoding prompts in Java classes.
 */
@Component
@Slf4j
public class ToolPromptRegistry {

    public static final String DEFAULT_PROMPTS_LOCATION = "classpath:prompts/tool-prompts.yml";

    private final Map<String, ToolPromptDefinition> toolPrompts;

    public ToolPromptRegistry() {
        this(new DefaultResourceLoader(), DEFAULT_PROMPTS_LOCATION);
    }

    public ToolPromptRegistry(
            ResourceLoader resourceLoader,
            @Value("${app.agent.prompts.tools-location:" + DEFAULT_PROMPTS_LOCATION + "}") String location
    ) {
        this.toolPrompts = loadToolPrompts(resourceLoader, location);
    }

    public ToolPromptDefinition getPromptDefinition(String toolName) {
        return toolPrompts.get(toolName);
    }

    public ToolSpecification enrich(ToolSpecification baseSpec) {
        if (baseSpec == null) {
            return null;
        }

        ToolPromptDefinition promptDef = toolPrompts.get(baseSpec.name());
        if (promptDef == null) {
            return baseSpec;
        }

        String description = (promptDef.getDescription() != null && !promptDef.getDescription().isBlank())
                ? promptDef.getDescription().trim()
                : baseSpec.description();

        JsonObjectSchema enrichedParameters = enrichParameters(baseSpec.parameters(), promptDef.getParameters());

        ToolSpecification.Builder builder = ToolSpecification.builder()
                .name(baseSpec.name())
                .description(description);

        if (enrichedParameters != null) {
            builder.parameters(enrichedParameters);
        }

        return builder.build();
    }

    private JsonObjectSchema enrichParameters(
            JsonObjectSchema baseParameters,
            Map<String, String> parameterPrompts
    ) {
        if (baseParameters == null || parameterPrompts == null || parameterPrompts.isEmpty()) {
            return baseParameters;
        }

        Map<String, JsonSchemaElement> baseProps = baseParameters.properties();
        if (baseProps == null || baseProps.isEmpty()) {
            return baseParameters;
        }

        Map<String, JsonSchemaElement> updatedProps = new LinkedHashMap<>();
        for (Map.Entry<String, JsonSchemaElement> entry : baseProps.entrySet()) {
            String paramName = entry.getKey();
            JsonSchemaElement element = entry.getValue();
            String externalParamDesc = parameterPrompts.get(paramName);

            if (externalParamDesc != null && !externalParamDesc.isBlank()) {
                updatedProps.put(paramName, applyDescription(element, externalParamDesc.trim()));
            } else {
                updatedProps.put(paramName, element);
            }
        }

        JsonObjectSchema.Builder schemaBuilder = JsonObjectSchema.builder()
                .description(baseParameters.description())
                .properties(updatedProps);

        if (baseParameters.required() != null) {
            schemaBuilder.required(baseParameters.required());
        }
        if (baseParameters.additionalProperties() != null) {
            schemaBuilder.additionalProperties(baseParameters.additionalProperties());
        }
        if (baseParameters.definitions() != null && !baseParameters.definitions().isEmpty()) {
            schemaBuilder.definitions(baseParameters.definitions());
        }

        return schemaBuilder.build();
    }

    private JsonSchemaElement applyDescription(JsonSchemaElement element, String description) {
        if (element instanceof JsonStringSchema) {
            return JsonStringSchema.builder().description(description).build();
        }
        if (element instanceof JsonBooleanSchema) {
            return JsonBooleanSchema.builder().description(description).build();
        }
        if (element instanceof JsonIntegerSchema) {
            return JsonIntegerSchema.builder().description(description).build();
        }
        if (element instanceof JsonNumberSchema) {
            return JsonNumberSchema.builder().description(description).build();
        }
        return element;
    }

    @SuppressWarnings("unchecked")
    private Map<String, ToolPromptDefinition> loadToolPrompts(ResourceLoader resourceLoader, String location) {
        Map<String, ToolPromptDefinition> loaded = new LinkedHashMap<>();
        try {
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists()) {
                log.warn("Tool prompts resource not found at {}", location);
                return loaded;
            }

            Yaml yaml = new Yaml();
            try (InputStream is = resource.getInputStream()) {
                Object rootObj = yaml.load(is);
                if (!(rootObj instanceof Map<?, ?> rootMap)) {
                    return loaded;
                }

                Object toolsNode = rootMap.get("tools");
                if (!(toolsNode instanceof Map<?, ?> toolsMap)) {
                    return loaded;
                }

                for (Map.Entry<?, ?> entry : toolsMap.entrySet()) {
                    String toolName = String.valueOf(entry.getKey());
                    if (entry.getValue() instanceof Map<?, ?> defMap) {
                        Object descObj = defMap.get("description");
                        String description = descObj != null ? String.valueOf(descObj).trim() : null;

                        Map<String, String> params = new LinkedHashMap<>();
                        Object paramsObj = defMap.get("parameters");
                        if (paramsObj instanceof Map<?, ?> rawParams) {
                            for (Map.Entry<?, ?> pEntry : rawParams.entrySet()) {
                                if (pEntry.getKey() != null && pEntry.getValue() != null) {
                                    params.put(String.valueOf(pEntry.getKey()), String.valueOf(pEntry.getValue()).trim());
                                }
                            }
                        }

                        loaded.put(toolName, ToolPromptDefinition.builder()
                                .description(description)
                                .parameters(params)
                                .build());
                    }
                }
            }
            log.info("Loaded {} external tool prompt definitions from {}", loaded.size(), location);
        } catch (Exception e) {
            log.error("Failed to load tool prompts from {}: {}", location, e.getMessage(), e);
        }
        return Collections.unmodifiableMap(loaded);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolPromptDefinition {
        private String description;
        @Builder.Default
        private Map<String, String> parameters = Collections.emptyMap();
    }
}
