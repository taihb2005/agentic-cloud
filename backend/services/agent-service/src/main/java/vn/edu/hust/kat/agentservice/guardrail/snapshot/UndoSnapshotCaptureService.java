package vn.edu.hust.kat.agentservice.guardrail.snapshot;

import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edu.hust.kat.agentservice.context.ChatTurnContext;
import vn.edu.hust.kat.agentservice.guardrail.snapshot.body.InverseActionType;
import vn.edu.hust.kat.agentservice.guardrail.snapshot.payload.UndoSnapshotPayload;
import vn.edu.hust.kat.agentservice.guardrail.undo.Undoable;

import java.util.UUID;

/**
 * Generic, switch-free snapshot capture coordinator.
 * Delegates pre-state and post-state snapshot capture directly to any target component
 * implementing {@link Undoable}, then serializes the typed {@link UndoSnapshotPayload} via Jackson.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UndoSnapshotCaptureService {

    private final JsonMapper jsonMapper;

    public UndoSnapshotPayload capturePreState(
            Object targetBean,
            UUID userId,
            String actionType,
            String targetNodeId,
            Object[] rawArgs
    ) {
        if (!(targetBean instanceof Undoable undoable)) {
            return null;
        }
        if (actionType == null || !undoable.supportedActionTypes().contains(actionType)) {
            return null;
        }
        try {
            return undoable.capturePreState(userId, actionType, targetNodeId, rawArgs);
        } catch (Exception e) {
            log.warn("Failed to capture pre-state snapshot for action {}: {}", actionType, e.getMessage());
            return null;
        }
    }

    public String finalizeAndSerialize(
            Object targetBean,
            UUID userId,
            String actionType,
            UndoSnapshotPayload preSnapshot,
            Object toolResult,
            ChatTurnContext turnContext
    ) {
        if (!(targetBean instanceof Undoable undoable) || preSnapshot == null) {
            return null;
        }
        try {
            UndoSnapshotPayload finalSnapshot = undoable.capturePostState(
                    userId,
                    actionType,
                    preSnapshot,
                    toolResult,
                    turnContext
            );
            if (finalSnapshot == null
                    || !finalSnapshot.isSupportUndo()
                    || finalSnapshot.getInverseActionType() == InverseActionType.NONE) {
                return null;
            }
            return jsonMapper.writeValueAsString(finalSnapshot);
        } catch (Exception e) {
            log.warn("Failed to finalize/serialize undo snapshot for action {}: {}", actionType, e.getMessage());
            return null;
        }
    }
}
