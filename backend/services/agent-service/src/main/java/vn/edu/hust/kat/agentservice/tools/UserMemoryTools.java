package vn.edu.hust.kat.agentservice.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.hust.kat.agentservice.context.ChatTurnContext;
import vn.edu.hust.kat.agentservice.context.UserContextManager;
import vn.edu.hust.kat.agentservice.guardrail.annotation.ReadAction;
import vn.edu.hust.kat.agentservice.guardrail.annotation.SafeAction;
import vn.edu.hust.kat.agentservice.guardrail.snapshot.body.InverseActionType;
import vn.edu.hust.kat.agentservice.guardrail.snapshot.payload.MemoryMutationUndoSnapshot;
import vn.edu.hust.kat.agentservice.guardrail.snapshot.payload.UndoSnapshotPayload;
import vn.edu.hust.kat.agentservice.guardrail.undo.Undoable;
import vn.edu.hust.kat.agentservice.memory.DualLayerMemoryManager;
import vn.edu.hust.kat.agentservice.memory.PgVectorMemoryStore;
import vn.edu.hust.kat.common.api.exception.BadRequestException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Tool descriptions and parameter prompts are loaded externally from {@code classpath:prompts/tool-prompts.yml}
 * via {@link vn.edu.hust.kat.agentservice.config.ToolPromptRegistry}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserMemoryTools implements ITools, Undoable {

    private static final Set<String> UNDOABLE_ACTIONS = Set.of(
            "UPDATE_USER_PREF", "DELETE_USER_PREF", "SAVE_USER_CONTEXT"
    );

    private final UserContextManager userContextManager;
    private final DualLayerMemoryManager memoryManager;

    @Tool
    @SafeAction(actionType = "UPDATE_USER_PREF", supportUndo = true)
    public String updateUserPreference(String preferenceKey, String preferenceValue) {
        String userId = userContextManager.getCurrentUserIdAsString();
        String id = memoryManager.upsertUserPreference(userId, preferenceKey, preferenceValue);
        ChatTurnContext turnContext = userContextManager.getCurrentTurnContext();
        if (turnContext != null) {
            turnContext.setLastCreatedResourceId(id);
        }
        log.info("Agent updated preference for user={}, key={}, memoryId={}", userId, preferenceKey, id);
        return String.format("Đã cập nhật sở thích '%s' thành: %s", preferenceKey, preferenceValue);
    }

    @Tool
    @SafeAction(actionType = "SAVE_USER_CONTEXT", supportUndo = true)
    public String saveUserContext(String contextType, String content) {
        String userId = userContextManager.getCurrentUserIdAsString();
        String id = memoryManager.saveUserMemory(userId, contextType, content);
        ChatTurnContext turnContext = userContextManager.getCurrentTurnContext();
        if (turnContext != null) {
            turnContext.setLastCreatedResourceId(id);
        }
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
    @SafeAction(actionType = "DELETE_USER_PREF", supportUndo = true)
    public String deleteUserPreference(String preferenceKey) {
        String userId = userContextManager.getCurrentUserIdAsString();
        memoryManager.deleteUserPreference(userId, preferenceKey);
        log.info("Agent deleted preference for user={}, key={}", userId, preferenceKey);
        return String.format("Đã xóa sở thích '%s' khỏi bộ nhớ dài hạn.", preferenceKey);
    }

    // =========================================================================
    // Undoable implementation
    // =========================================================================

    @Override
    public Set<String> supportedActionTypes() {
        return UNDOABLE_ACTIONS;
    }

    @Override
    public UndoSnapshotPayload capturePreState(
            UUID userId,
            String actionType,
            String targetNodeId,
            Object[] rawArgs
    ) {
        if (memoryManager == null || rawArgs == null || rawArgs.length == 0) {
            return null;
        }
        String userIdStr = userId.toString();

        if ("UPDATE_USER_PREF".equals(actionType)) {
            String rawKey = rawArgs[0] != null ? rawArgs[0].toString().trim() : null;
            String newVal = rawArgs.length > 1 && rawArgs[1] != null ? rawArgs[1].toString().trim() : null;
            if (rawKey == null || rawKey.isBlank()) {
                return null;
            }
            String normalizedKey = PgVectorMemoryStore.normalizePrefKey(rawKey);
            Optional<PgVectorMemoryStore.PreferenceSnapshot> existing = memoryManager.findUserPreference(userIdStr, normalizedKey);
            boolean existedBefore = existing.isPresent();

            return MemoryMutationUndoSnapshot.builder()
                    .category(UndoSnapshotPayload.SnapshotCategory.MEMORY)
                    .actionType(actionType)
                    .inverseActionType(existedBefore
                            ? InverseActionType.RESTORE_USER_PREFERENCE
                            : InverseActionType.DELETE_USER_PREFERENCE)
                    .memoryType(PgVectorMemoryStore.TYPE_USER_PREFERENCE)
                    .preferenceKey(normalizedKey)
                    .existedBefore(existedBefore)
                    .previousValue(existedBefore ? existing.get().value() : null)
                    .newValue(newVal)
                    .build();
        }

        if ("DELETE_USER_PREF".equals(actionType)) {
            String rawKey = rawArgs[0] != null ? rawArgs[0].toString().trim() : null;
            if (rawKey == null || rawKey.isBlank()) {
                return null;
            }
            String normalizedKey = PgVectorMemoryStore.normalizePrefKey(rawKey);
            Optional<PgVectorMemoryStore.PreferenceSnapshot> existing = memoryManager.findUserPreference(userIdStr, normalizedKey);
            if (existing.isEmpty()) {
                return MemoryMutationUndoSnapshot.builder()
                        .category(UndoSnapshotPayload.SnapshotCategory.MEMORY)
                        .actionType(actionType)
                        .supportUndo(false)
                        .inverseActionType(InverseActionType.NONE)
                        .preferenceKey(normalizedKey)
                        .existedBefore(false)
                        .build();
            }
            return MemoryMutationUndoSnapshot.builder()
                    .category(UndoSnapshotPayload.SnapshotCategory.MEMORY)
                    .actionType(actionType)
                    .inverseActionType(InverseActionType.RESTORE_USER_PREFERENCE)
                    .memoryId(existing.get().memoryId())
                    .memoryType(PgVectorMemoryStore.TYPE_USER_PREFERENCE)
                    .preferenceKey(normalizedKey)
                    .existedBefore(true)
                    .previousValue(existing.get().value())
                    .build();
        }

        if ("SAVE_USER_CONTEXT".equals(actionType)) {
            String memoryType = rawArgs[0] != null ? rawArgs[0].toString().trim() : "USER_CONTEXT";
            String content = rawArgs.length > 1 && rawArgs[1] != null ? rawArgs[1].toString().trim() : null;
            return MemoryMutationUndoSnapshot.builder()
                    .category(UndoSnapshotPayload.SnapshotCategory.MEMORY)
                    .actionType(actionType)
                    .inverseActionType(InverseActionType.DELETE_CREATED_MEMORY)
                    .memoryType(memoryType)
                    .newValue(content)
                    .build();
        }

        return null;
    }

    @Override
    public UndoSnapshotPayload capturePostState(
            UUID userId,
            String actionType,
            UndoSnapshotPayload preSnapshot,
            Object toolResult,
            ChatTurnContext turnContext
    ) {
        if (preSnapshot instanceof MemoryMutationUndoSnapshot memSnap) {
            if (turnContext != null && turnContext.getLastCreatedResourceId() != null) {
                memSnap.setMemoryId(turnContext.getLastCreatedResourceId());
            }
            if ("SAVE_USER_CONTEXT".equals(actionType)
                    && (memSnap.getMemoryId() == null || memSnap.getMemoryId().isBlank())) {
                memSnap.setSupportUndo(false);
                memSnap.setInverseActionType(InverseActionType.NONE);
            }
        }
        return preSnapshot;
    }

    @Override
    public String revert(UUID userId, UndoSnapshotPayload snapshot) {
        if (!(snapshot instanceof MemoryMutationUndoSnapshot snap)) {
            throw new BadRequestException("Snapshot payload không hợp lệ cho UserMemoryTools");
        }
        InverseActionType inverseAction = snap.getInverseActionType();
        if (inverseAction == null || inverseAction == InverseActionType.NONE) {
            throw new BadRequestException("Hành động bộ nhớ này không hỗ trợ hoàn tác");
        }

        String userIdStr = userId.toString();
        return switch (inverseAction) {
            case RESTORE_USER_PREFERENCE -> {
                requireNonBlank(snap.getPreferenceKey(), "preferenceKey");
                requireNonBlank(snap.getPreviousValue(), "previousValue");
                memoryManager.upsertUserPreference(userIdStr, snap.getPreferenceKey(), snap.getPreviousValue());
                yield String.format("Đã khôi phục sở thích '%s' về giá trị cũ", snap.getPreferenceKey());
            }
            case DELETE_USER_PREFERENCE -> {
                requireNonBlank(snap.getPreferenceKey(), "preferenceKey");
                memoryManager.deleteUserPreference(userIdStr, snap.getPreferenceKey());
                yield String.format("Đã xóa sở thích '%s' vừa thêm mới", snap.getPreferenceKey());
            }
            case DELETE_CREATED_MEMORY -> {
                requireNonBlank(snap.getMemoryId(), "memoryId");
                memoryManager.deleteUserMemoryById(userIdStr, snap.getMemoryId());
                yield String.format("Đã xóa ngữ cảnh dài hạn vừa lưu (memoryId=%s)", snap.getMemoryId());
            }
            case RESTORE_DELETED_MEMORY -> {
                String memType = (snap.getMemoryType() != null && !snap.getMemoryType().isBlank())
                        ? snap.getMemoryType()
                        : "USER_CONTEXT";
                requireNonBlank(snap.getPreviousValue(), "previousValue");
                memoryManager.saveUserMemory(userIdStr, memType, snap.getPreviousValue());
                yield String.format("Đã khôi phục ngữ cảnh dài hạn (%s)", memType);
            }
            default -> throw new BadRequestException("UserMemoryTools không hỗ trợ inverseActionType=" + inverseAction);
        };
    }

    private void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Thiếu trường '" + field + "' trong memory undo snapshot để hoàn tác");
        }
    }
}
