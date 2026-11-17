package vn.edu.hust.kat.agentservice.core;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

public interface CloudAgent {

    String SYSTEM_PROMPT_RESOURCE = "prompts/system-prompt.txt";

    @SystemMessage(fromResource = SYSTEM_PROMPT_RESOURCE)
    String chat(@MemoryId String memoryId, @UserMessage String message);

    @SystemMessage(fromResource = SYSTEM_PROMPT_RESOURCE)
    TokenStream chatStream(@MemoryId String memoryId, @UserMessage String message);
}
