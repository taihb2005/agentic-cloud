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
public class MemoryMutationUndoSnapshot extends UndoSnapshotPayload {

    /** ID của vector/memory segment trong pgvector (agent_long_term_memory) */
    private String memoryId;

    /** Loại bộ nhớ dài hạn: USER_PREFERENCE, USER_FACT, WORKFLOW_HABIT, NAMING_RULE... */
    private String memoryType;

    /** Khóa sở thích đã chuẩn hóa (dùng cho UPDATE_USER_PREF / DELETE_USER_PREF) */
    private String preferenceKey;

    /** Cờ xác định khóa sở thích đã tồn tại trước khi cập nhật hay chưa */
    private Boolean existedBefore;

    /** Giá trị sở thích hoặc nội dung ngữ cảnh trước khi thay đổi/xóa */
    private String previousValue;

    /** Giá trị sở thích hoặc nội dung ngữ cảnh mới sau khi thêm/cập nhật */
    private String newValue;
}
