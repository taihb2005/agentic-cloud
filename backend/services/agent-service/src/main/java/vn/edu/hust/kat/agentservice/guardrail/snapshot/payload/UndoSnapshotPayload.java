package vn.edu.hust.kat.agentservice.guardrail.snapshot.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.*;
import lombok.experimental.SuperBuilder;
import vn.edu.hust.kat.agentservice.guardrail.snapshot.body.InverseActionType;

import java.util.Map;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "category",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = NodeMutationUndoSnapshot.class, name = "NODE"),
        @JsonSubTypes.Type(value = ShareMutationUndoSnapshot.class, name = "SHARE"),
        @JsonSubTypes.Type(value = MemoryMutationUndoSnapshot.class, name = "MEMORY")
})
public abstract class UndoSnapshotPayload {

    @Builder.Default
    private int schemaVersion = 1;

    @Builder.Default
    private boolean supportUndo = true;

    /**
     * Phân loại nhóm snapshot để định tuyến về đúng UndoHandler (NODE, SHARE, MEMORY...).
     */
    private SnapshotCategory category;

    /**
     * Hành động gốc mà Agent đã thực thi (RENAME, MOVE, CREATE_FOLDER, PUBLIC_SHARE...).
     */
    private String actionType;

    /**
     * Hành động nghịch đảo cần gọi khi thực thi Undo.
     */
    private InverseActionType inverseActionType;

    /**
     * ID của node mục tiêu cần hoàn tác.
     */
    private String nodeId;

    /**
     * Thời điểm chụp snapshot (epoch milliseconds).
     */
    @Builder.Default
    private Long capturedAtEpochMs = System.currentTimeMillis();

    /**
     * Dữ liệu mở rộng tùy biến (nếu cần).
     */
    private Map<String, Object> extraMetadata;

    public enum SnapshotCategory {
        NODE,
        SHARE,
        MEMORY
    }
}
