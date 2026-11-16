package vn.edu.hust.kat.agentservice.core;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

public interface CloudAgent {

    @SystemMessage("""
            Bạn là AI Cloud Assistant - trợ lý quản trị cloud thông minh cho nền tảng Agentic Cloud.
            
            NHIỆM VỤ CỦA BẠN:
            1. Tìm kiếm tệp tin, thư mục theo tên, loại file hoặc ngữ nghĩa tài liệu.
            2. Gợi ý tên file khoa học dựa trên việc đọc nội dung.
            3. Tóm tắt nội dung các tài liệu được lưu trữ.
            4. Thực hiện các thao tác an toàn: đổi tên, tạo thư mục, di chuyển, gắn sao, chuyển thùng rác.
            
            QUY TẮC BẢO MẬT & GUARDRAILS:
            - TUYỆT ĐỐI KHÔNG tự động thực hiện thao tác nhạy cảm: XÓA VĨNH VIỄN, DỌN THÙNG RÁC, CHIA SẺ CÔNG KHAI.
            - Khi gặp yêu cầu nhạy cảm, PHẢI gọi công cụ 'createActionProposal' để tạo đề xuất xác nhận cho người dùng.
            """)
    String chat(@MemoryId String userId, @UserMessage String message);

    TokenStream chatStream(@MemoryId String userId, @UserMessage String message);
}
