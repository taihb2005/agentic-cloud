package vn.edu.hust.kat.agentservice.guardrail.snapshot.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.SuperBuilder;
import vn.edu.hust.kat.agentservice.guardrail.snapshot.body.ShareConfigState;
import vn.edu.hust.kat.agentservice.guardrail.snapshot.body.SharedUserEntry;

import java.util.List;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShareMutationUndoSnapshot extends UndoSnapshotPayload {

    private Boolean isFolder;

    /** Cấu hình chia sẻ trước khi thực thi tool */
    private ShareConfigState previousShareState;

    /** Cấu hình chia sẻ sau khi thực thi tool */
    private ShareConfigState newShareState;

    /** Danh sách userId vừa được cấp thêm quyền (dùng để gỡ quyền khi Undo) */
    private List<String> addedSharedUserIds;

    /** Danh sách user vừa bị thu hồi quyền kèm permission cũ (dùng để cấp lại khi Undo) */
    private List<SharedUserEntry> removedSharedUsers;
}
