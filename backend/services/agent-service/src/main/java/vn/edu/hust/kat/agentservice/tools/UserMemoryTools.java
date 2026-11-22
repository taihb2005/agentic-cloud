package vn.edu.hust.kat.agentservice.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.hust.kat.agentservice.context.UserContextManager;
import vn.edu.hust.kat.agentservice.guardrail.annotation.ReadAction;
import vn.edu.hust.kat.agentservice.guardrail.annotation.SafeAction;
import vn.edu.hust.kat.agentservice.memory.DualLayerMemoryManager;

import java.util.List;

/**
 * Tool descriptions and parameter prompts are loaded externally from {@code classpath:prompts/tool-prompts.yml}
 * via {@link vn.edu.hust.kat.agentservice.config.ToolPromptRegistry}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserMemoryTools implements ITools {

    private final UserContextManager userContextManager;
    private final DualLayerMemoryManager memoryManager;

    @Tool
    @SafeAction(actionType = "UPDATE_USER_PREF", supportUndo = false)
    public String updateUserPreference(String preferenceKey, String preferenceValue) {
        String userId = userContextManager.getCurrentUserIdAsString();
        String id = memoryManager.upsertUserPreference(userId, preferenceKey, preferenceValue);
        log.info("Agent updated preference for user={}, key={}, memoryId={}", userId, preferenceKey, id);
        return String.format("Đã cập nhật sở thích '%s' thành: %s", preferenceKey, preferenceValue);
    }

    @Tool
    @SafeAction(actionType = "SAVE_USER_CONTEXT", supportUndo = false)
    public String saveUserContext(String contextType, String content) {
        String userId = userContextManager.getCurrentUserIdAsString();
        String id = memoryManager.saveUserMemory(userId, contextType, content);
        log.info("Agent saved long-term context for user={}, type={}, memoryId={}", userId, contextType, id);
        return String.format("Đã lưu ngữ cảnh dài hạn (%s): %s", contextType, content);
    }

    @Tool
    @ReadAction(actionType = "READ_USER_CONTEXT", description = "Search user long-term preferences and context")
    public String recallUserContext(String query) {
        String userId = userContextManager.getCurrentUserIdAsString();
        List<String> matches = memoryManager.searchUserSemanticMemory(userId, query, 5);
        if (matches.isEmpty()) {
            return "Chưa tìm thấy sở thích hoặc ngữ cảnh dài hạn nào phù hợp.";
        }
        return String.join("\n", matches);
    }

    @Tool
    @SafeAction(actionType = "DELETE_USER_PREF", supportUndo = false)
    public String deleteUserPreference(String preferenceKey) {
        String userId = userContextManager.getCurrentUserIdAsString();
        memoryManager.deleteUserPreference(userId, preferenceKey);
        log.info("Agent deleted preference for user={}, key={}", userId, preferenceKey);
        return String.format("Đã xóa sở thích '%s' khỏi bộ nhớ dài hạn.", preferenceKey);
    }
}
