package vn.edu.hust.kat.agentservice.guardrail.snapshot.body;

public enum InverseActionType {
    // --- Nhóm thao tác File / Folder (Node) ---
    RENAME_NODE,
    MOVE_NODE,
    TOGGLE_STAR_NODE,
    RESTORE_FROM_TRASH,
    SOFT_DELETE_CREATED_NODE,

    // --- Nhóm thao tác Chia sẻ & Liên kết công khai (Share & Link) ---
    REVOKE_ALL_SHARE,
    RESTORE_SHARE_CONFIG,
    REMOVE_SHARED_USERS,
    RESTORE_REMOVED_SHARED_USERS,
    RESTORE_SHARE_PERMISSION,

    // --- Nhóm thao tác Bộ nhớ & Sở thích dài hạn (Memory) ---
    RESTORE_USER_PREFERENCE,
    DELETE_USER_PREFERENCE,
    DELETE_CREATED_MEMORY,
    RESTORE_DELETED_MEMORY,

    // --- Mặc định khi không hỗ trợ hoàn tác ---
    NONE
}
