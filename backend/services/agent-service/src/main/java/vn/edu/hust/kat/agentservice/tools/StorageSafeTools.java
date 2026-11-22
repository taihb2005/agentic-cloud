package vn.edu.hust.kat.agentservice.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.hust.kat.agentservice.context.UserContextManager;
import vn.edu.hust.kat.agentservice.grpc.StorageGrpcClient;
import vn.edu.hust.kat.agentservice.guardrail.annotation.SafeAction;
import vn.edu.hust.kat.proto.storage.v1.NodeItemResponse;
import vn.edu.hust.kat.proto.storage.v1.SoftDeleteNodeGrpcResponse;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class StorageSafeTools implements ITools {

    private final UserContextManager userContextManager;
    private final StorageGrpcClient storageGrpcClient;

    @Tool("Đổi tên của tệp tin hoặc thư mục sang tên mới")
    @SafeAction(actionType = "RENAME", supportUndo = true)
    public String renameNode(
            @P("ID của tệp tin hoặc thư mục cần đổi tên") String nodeId,
            @P("Tên mới cho tệp tin hoặc thư mục") String newName
    ) {
        UUID userId = userContextManager.getCurrentUserId();
        log.info("Executing safe rename via gRPC: user={}, node={}, newName={}", userId, nodeId, newName);

        NodeItemResponse updated = storageGrpcClient.renameNode(userId, nodeId, newName);
        return String.format("Thành công: Đã đổi tên node %s thành '%s'", updated.getId(), updated.getName());
    }

    @Tool("Tạo thư mục mới trong một thư mục cha (hoặc thư mục gốc nếu parentId để trống)")
    @SafeAction(actionType = "CREATE_FOLDER", supportUndo = true)
    public String createFolder(
            @P("Tên thư mục mới cần tạo") String folderName,
            @P("ID của thư mục cha (để trống hoặc truyền 'root' nếu tạo ở thư mục gốc)") String parentId
    ) {
        UUID userId = userContextManager.getCurrentUserId();
        log.info("Executing safe folder creation via gRPC: user={}, name={}, parent={}", userId, folderName, parentId);

        NodeItemResponse created = storageGrpcClient.createFolder(userId, parentId, folderName);
        return String.format("Thành công: Đã tạo thư mục '%s' với ID: %s", created.getName(), created.getId());
    }

    @Tool("Di chuyển một tệp tin hoặc thư mục vào một thư mục đích")
    @SafeAction(actionType = "MOVE", supportUndo = true)
    public String moveNode(
            @P("ID của tệp tin hoặc thư mục cần di chuyển") String nodeId,
            @P("ID của thư mục đích (hoặc 'root' để di chuyển về thư mục gốc)") String targetFolderId
    ) {
        UUID userId = userContextManager.getCurrentUserId();
        log.info("Executing safe move via gRPC: user={}, node={}, targetFolder={}", userId, nodeId, targetFolderId);

        NodeItemResponse moved = storageGrpcClient.moveNode(userId, nodeId, targetFolderId);
        String destId = !moved.getParentId().isBlank() ? moved.getParentId() : targetFolderId;
        return String.format("Thành công: Đã di chuyển node %s ('%s') vào thư mục %s", moved.getId(), moved.getName(), destId);
    }

    @Tool("Đưa tệp tin hoặc thư mục vào thùng rác (xóa mềm - có thể khôi phục được)")
    @SafeAction(actionType = "SOFT_DELETE", supportUndo = true)
    public String moveToTrash(
            @P("ID của tệp tin hoặc thư mục cần chuyển vào thùng rác") String nodeId
    ) {
        UUID userId = userContextManager.getCurrentUserId();
        log.info("Executing safe soft-delete via gRPC: user={}, node={}", userId, nodeId);

        SoftDeleteNodeGrpcResponse deleted = storageGrpcClient.softDeleteNode(userId, nodeId);
        return String.format(
                "Thành công: Đã đưa node %s ('%s') vào thùng rác (batchId=%s). Người dùng có thể khôi phục bất cứ lúc nào.",
                deleted.getNodeId(),
                deleted.getName(),
                deleted.getDeleteBatchId()
        );
    }

    @Tool("Đánh dấu sao (yêu thích) hoặc bỏ đánh dấu sao cho một tệp tin/thư mục")
    @SafeAction(actionType = "STAR", supportUndo = true)
    public String toggleStar(
            @P("ID của tệp tin hoặc thư mục cần gắn/bỏ sao") String nodeId,
            @P("true để gắn sao, false để bỏ sao") boolean starred
    ) {
        UUID userId = userContextManager.getCurrentUserId();
        log.info("Executing safe star toggle via gRPC: user={}, node={}, starred={}", userId, nodeId, starred);

        NodeItemResponse updated = storageGrpcClient.toggleStarNode(userId, nodeId, starred);
        return String.format(
                "Thành công: Đã %s sao cho node %s ('%s')",
                updated.getIsStarred() ? "gắn" : "bỏ",
                updated.getId(),
                updated.getName()
        );
    }
}
