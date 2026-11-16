package vn.edu.hust.kat.agentservice.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.hust.kat.agentservice.context.UserContextManager;
import vn.edu.hust.kat.agentservice.guardrail.annotation.ReadAction;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class StorageReadTools {

    private final UserContextManager userContextManager;

    @Tool("Tìm kiếm tệp tin hoặc thư mục của người dùng theo từ khóa tên hoặc đuôi mở rộng")
    @ReadAction(description = "Search files by name pattern")
    public String searchFilesByName(String query) {
        UUID userId = userContextManager.getCurrentUserId();
        log.info("Agent searching files for user: {}, query: {}", userId, query);
        // Tích hợp gọi StorageService gRPC hoặc REST
        return String.format("[{\"id\":\"node-101\",\"name\":\"%s_sample.pdf\",\"isFolder\":false,\"sizeBytes\":1048576,\"status\":\"READY\"}]", query);
    }

    @Tool("Đọc nội dung văn bản trích xuất của một tệp tin dựa theo nodeId")
    @ReadAction(description = "Read text content of a file")
    public String readFileContent(String nodeId) {
        UUID userId = userContextManager.getCurrentUserId();
        log.info("Agent reading file content for user: {}, nodeId: {}", userId, nodeId);
        return "Nội dung trích xuất của tệp " + nodeId + ": Hợp đồng thuê văn phòng năm 2026, các điều khoản thanh toán...";
    }

    @Tool("Xem dung lượng lưu trữ hiện tại và quota tối đa của người dùng")
    @ReadAction(description = "Get storage quota")
    public String getUserStorageQuota() {
        UUID userId = userContextManager.getCurrentUserId();
        log.info("Agent checking quota for user: {}", userId);
        return "Dung lượng đã dùng: 2.5 GB / 15.0 GB (16.6%)";
    }
}
