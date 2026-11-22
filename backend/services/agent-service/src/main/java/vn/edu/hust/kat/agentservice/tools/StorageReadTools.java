package vn.edu.hust.kat.agentservice.tools;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.hust.kat.agentservice.context.UserContextManager;
import vn.edu.hust.kat.agentservice.grpc.StorageGrpcClient;
import vn.edu.hust.kat.agentservice.guardrail.annotation.ReadAction;
import vn.edu.hust.kat.proto.storage.v1.GetUserQuotaResponse;
import vn.edu.hust.kat.proto.storage.v1.ReadFileContentResponse;
import vn.edu.hust.kat.proto.storage.v1.SearchNodesResponse;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class StorageReadTools implements ITools {

    private static final Gson Gson = new Gson();

    private final UserContextManager userContextManager;
    private final StorageGrpcClient storageGrpcClient;

    @Tool
    @ReadAction(actionType = "READ_SEARCH", description = "Search or browse child nodes in folder")
    public String searchFilesByName(
            String parentId,
            String nodeType,
            String name,
            String mimeType,
            Integer page,
            Integer size,
            String sort
    ) {
        UUID userId = userContextManager.getCurrentUserId();
        int pageNum = (page != null && page >= 0) ? page : 0;
        int pageSize = (size != null && size > 0) ? size : 20;
        List<String> sorts = (sort != null && !sort.isBlank())
                ? List.of(sort.trim())
                : List.of("name,asc");

        log.info("Agent searching child nodes via gRPC: user={}, parentId={}, nodeType={}, name={}, mimeType={}, page={}, size={}, sort={}",
                userId, parentId, nodeType, name, mimeType, pageNum, pageSize, sorts);

        SearchNodesResponse response = storageGrpcClient.searchNodes(
                userId,
                parentId,
                nodeType,
                name,
                mimeType,
                pageNum,
                pageSize,
                sorts
        );

        var items = response.getNodesList().stream()
                .map(node -> Map.of(
                        "id", node.getId(),
                        "parentId", node.getParentId(),
                        "name", node.getName(),
                        "isFolder", node.getIsFolder(),
                        "sizeBytes", node.getSizeBytes(),
                        "mimeType", node.getMimeType(),
                        "isStarred", node.getIsStarred(),
                        "status", node.getNodeStatus()
                ))
                .toList();

        return Gson.toJson(items);
    }

    @Tool
    @ReadAction(actionType = "READ_FILE_CONTENT", description = "Read text content of a file")
    public String readFileContent(String nodeId) {
        UUID userId = userContextManager.getCurrentUserId();
        log.info("Agent reading file content via gRPC for user: {}, nodeId: {}", userId, nodeId);

        ReadFileContentResponse response = storageGrpcClient.readFileContent(userId, nodeId);
        return String.format(
                "Nội dung của tệp '%s' (nodeId=%s, mimeType=%s, sizeBytes=%d):\n%s",
                response.getName(),
                response.getNodeId(),
                response.getMimeType(),
                response.getSizeBytes(),
                response.getContent()
        );
    }

    @Tool
    @ReadAction(actionType = "READ_QUOTA", description = "Get storage quota")
    public String getUserStorageQuota() {
        UUID userId = userContextManager.getCurrentUserId();
        log.info("Agent checking quota via gRPC for user: {}", userId);

        GetUserQuotaResponse quota = storageGrpcClient.getUserQuota(userId);
        return String.format(
                Locale.US,
                "Dung lượng đã dùng: %s / %s (%.2f%%) - Trạng thái: %s (currentBytes=%d, maxBytes=%d)",
                formatBytes(quota.getCurrentQuotaBytes()),
                formatBytes(quota.getMaxQuotaBytes()),
                quota.getUsagePercentage(),
                quota.getStatus(),
                quota.getCurrentQuotaBytes(),
                quota.getMaxQuotaBytes()
        );
    }

    private String formatBytes(long bytes) {
        double gb = bytes / (1024.0 * 1024.0 * 1024.0);
        if (gb >= 1.0) {
            return String.format(Locale.US, "%.2f GB", gb);
        }
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 1.0) {
            return String.format(Locale.US, "%.2f MB", mb);
        }
        double kb = bytes / 1024.0;
        if (kb >= 1.0) {
            return String.format(Locale.US, "%.2f KB", kb);
        }
        return bytes + " B";
    }
}
