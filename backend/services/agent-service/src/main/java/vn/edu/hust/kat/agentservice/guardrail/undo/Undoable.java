package vn.edu.hust.kat.agentservice.guardrail.undo;

import vn.edu.hust.kat.agentservice.context.ChatTurnContext;
import vn.edu.hust.kat.agentservice.guardrail.snapshot.payload.UndoSnapshotPayload;

import java.util.Set;
import java.util.UUID;

/**
 * Contract for tool components whose mutating actions support snapshot capture and revert (Undo).
 * Any action declared in {@link #supportedActionTypes()} on an {@code Undoable} implementation
 * can automatically capture pre/post state and be reverted via {@link #revert(UUID, UndoSnapshotPayload)}.
 */
public interface Undoable {

    /**
     * Set of actionType keys (matching @SafeAction / @SensitiveAction) supported by this component.
     */
    Set<String> supportedActionTypes();

    /**
     * Captures the pre-mutation state snapshot before executing the tool action.
     */
    UndoSnapshotPayload capturePreState(
            UUID userId,
            String actionType,
            String targetNodeId,
            Object[] rawArgs
    );

    /**
     * Optionally enriches the snapshot after successful tool execution (defaults to returning preSnapshot).
     */
    default UndoSnapshotPayload capturePostState(
            UUID userId,
            String actionType,
            UndoSnapshotPayload preSnapshot,
            Object toolResult,
            ChatTurnContext turnContext
    ) {
        return preSnapshot;
    }

    /**
     * Executes the inverse compensating operation to revert the action.
     *
     * @return human-readable description of what was reverted
     */
    String revert(UUID userId, UndoSnapshotPayload snapshot);
}
