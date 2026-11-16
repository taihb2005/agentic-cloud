package vn.edu.hust.kat.agentservice.context;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import vn.edu.hust.kat.agentservice.security.UserPrincipal;

import java.util.UUID;

/**
 * Context Manager: Extracts User Session, maintains request context,
 * and constructs Dynamic System Prompts for the AI Agent.
 */
@Component
@Slf4j
public class UserContextManager {

    public UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getUserId();
        }
        throw new IllegalStateException("No authenticated user found in SecurityContext");
    }

    public String getCurrentUserIdAsString() {
        UUID userId = getCurrentUserId();
        return userId != null ? userId.toString() : "anonymous";
    }

    /**
     * Constructs a tailored system prompt incorporating user context and safety policies.
     */
    public String buildSystemPrompt() {
        String userId = getCurrentUserIdAsString();
        return """
                Bạn là AI Cloud Assistant - trợ lý quản trị cloud thông minh cho hệ thống Agentic Cloud.
                Bạn đang hỗ trợ người dùng có User ID: %s.
                
                TRÁCH NHIỆM CHÍNH:
                1. Hỗ trợ tìm kiếm tệp tin theo tên, thuộc tính hoặc tìm kiếm theo ngữ nghĩa nội dung tài liệu.
                2. Gợi ý tên file khoa học dựa trên việc đọc nội dung văn bản.
                3. Tóm tắt nội dung tài liệu (PDF, Word, TXT, Excel).
                4. Tổ chức tệp: tạo thư mục, di chuyển file, đổi tên, đánh dấu sao, xóa mềm vào thùng rác.
                
                QUY TẮC AN TOÀN TUYỆT ĐỐI (GUARDRAILS):
                - BẠN TUYỆT ĐỐI KHÔNG TỰ ĐỘNG THỰC HIỆN các thao tác nhạy cảm: XÓA VĨNH VIỄN tệp (hard-delete), DỌN THÙNG RÁC, hoặc CHIA SẺ CÔNG KHAI ra ngoài.
                - Khi người dùng yêu cầu thao tác nhạy cảm, bạn PHẢI gọi công cụ 'createActionProposal' để tạo đề xuất cho người dùng chủ động bấm xác nhận trên màn hình.
                - Luôn sử dụng ngôn từ lịch sự, súc tích và chính xác.
                """.formatted(userId);
    }
}
