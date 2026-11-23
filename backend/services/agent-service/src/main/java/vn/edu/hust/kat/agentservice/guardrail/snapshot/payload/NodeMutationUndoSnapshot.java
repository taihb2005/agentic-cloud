package vn.edu.hust.kat.agentservice.guardrail.snapshot.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodeMutationUndoSnapshot extends UndoSnapshotPayload {

    private Boolean isFolder;

    // --- Pre-mutation state (Trạng thái trước khi thực thi) ---
    private String previousName;
    private String previousParentId;
    private Boolean previousStarred;
    private String previousStatus;

    // --- Post-mutation state (Trạng thái sau khi thực thi) ---
    private String newName;
    private String newParentId;
    private Boolean newStarred;
    private String newStatus;

    /** ID thư mục/node vừa được tạo mới (dùng cho CREATE_FOLDER) */
    private String createdNodeId;

    /** Mã batch xóa mềm (dùng cho SOFT_DELETE để khôi phục cả cây thư mục con) */
    private String deleteBatchId;
}
