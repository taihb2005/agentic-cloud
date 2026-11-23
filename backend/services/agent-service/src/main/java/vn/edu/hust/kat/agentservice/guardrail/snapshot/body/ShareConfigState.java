package vn.edu.hust.kat.agentservice.guardrail.snapshot.body;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShareConfigState {

    /** Chế độ chia sẻ: PRIVATE (hoặc NONE), PUBLIC_LINK, USER_GROUP */
    private String shareMode;

    /** Trạng thái: NONE, ACTIVE, EXPIRED, REVOKED */
    private String status;

    /** Quyền truy cập: VIEW hoặc EDIT */
    private String permission;

    private Boolean isPublic;

    private String shareToken;

    private String sharedUrl;

    private Boolean isPasswordProtected;

    /** Thời điểm hết hạn (ISO-8601) */
    private String expiresAt;

    private Integer maxAccessCount;

    /** Danh sách người dùng được chia sẻ (khi ở chế độ USER_GROUP) */
    private List<SharedUserEntry> sharedUsers;
}
