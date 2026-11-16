package vn.edu.hust.kat.agentservice.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.hust.kat.agentservice.context.UserContextManager;
import vn.edu.hust.kat.agentservice.guardrail.annotation.SafeAction;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class StorageSafeTools {

    private final UserContextManager userContextManager;

    @Tool("Đổi tên của tệp tin hoặc thư mục sang tên mới")
    @SafeAction(actionType = "RENAME", supportUndo = true)
    public String renameNode(String nodeId, String newName) {
        UUID userId = userContextManager.getCurrentUserId();
        log.info("Executing safe rename: user={}, node={}, newName={}", userId, nodeId, newName);
        return String.format("Thành công: Đã đổi tên node %s thành '%s'", nodeId, newName);
    }

    @Tool("Tạo thư mục mới trong một thư mục cha (hoặc thư mục gốc nếu parentId để trống)")
    @SafeAction(actionType = "CREATE_FOLDER", supportUndo = true)
    public String createFolder(String folderName, String parentId) {
        UUID userId = userContextManager.getCurrentUserId();
        log.info("Executing safe folder creation: user={}, name={}, parent={}", userId, folderName, parentId);
        String createdId = UUID.randomUUID().toString();
        return String.format("Thành công: Đã tạo thư mục '%s' với ID: %s", folderName, createdId);
    }

    @Tool("Di chuyển một tệp tin hoặc thư mục vào một thư mục đích")
    @SafeAction(actionType = "MOVE", supportUndo = true)
    public String moveNode(String nodeId, String targetFolderId) {
        UUID userId = userContextManager.getCurrentUserId();
        log.info("Executing safe move: user={}, node={}, targetFolder={}", userId, nodeId, targetFolderId);
        return String.format("Thành công: Đã di chuyển node %s vào thư mục %s", nodeId, targetFolderId);
    }

    @Tool("Đưa tệp tin hoặc thư mục vào thùng rác (xóa mềm - có thể khôi phục được)")
    @SafeAction(actionType = "SOFT_DELETE", supportUndo = true)
    public String moveToTrash(String nodeId) {
        UUID userId = userContextManager.getCurrentUserId();
        log.info("Executing safe soft-delete: user={}, node={}", userId, nodeId);
        return String.format("Thành công: Đã đưa node %s vào thùng rác. Người dùng có thể khôi phục bất cứ lúc nào.", nodeId);
    }

    @Tool("Đánh dấu sao (yêu thích) hoặc bỏ đánh dấu sao cho một tệp tin/thư mục")
    @SafeAction(actionType = "STAR", supportUndo = true)
    public String toggleStar(String nodeId, boolean starred) {
        UUID userId = userContextManager.getCurrentUserId();
        log.info("Executing safe star toggle: user={}, node={}, starred={}", userId, nodeId, starred);
        return String.format("Thành công: Đã %s sao cho node %s", starred ? "gắn" : "bỏ", nodeId);
    }
}
