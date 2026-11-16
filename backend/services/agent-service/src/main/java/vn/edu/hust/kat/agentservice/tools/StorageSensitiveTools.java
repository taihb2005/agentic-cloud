package vn.edu.hust.kat.agentservice.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.hust.kat.agentservice.context.UserContextManager;
import vn.edu.hust.kat.agentservice.guardrail.annotation.SensitiveAction;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class StorageSensitiveTools {

    private final UserContextManager userContextManager;

    @Tool("Tạo đề xuất xác nhận (Action Proposal) khi người dùng yêu cầu XÓA VĨNH VIỄN, DỌN THÙNG RÁC, hoặc CHIA SẺ CÔNG KHAI")
    public String createActionProposal(String actionType, String targetNodeId, String explanation) {
        UUID userId = userContextManager.getCurrentUserId();
        String proposalToken = UUID.randomUUID().toString();
        log.warn("Created sensitive action proposal: user={}, action={}, node={}, token={}",
                userId, actionType, targetNodeId, proposalToken);

        return String.format(
                "{\"type\":\"PROPOSAL_CONFIRMATION\",\"actionType\":\"%s\",\"targetNodeId\":\"%s\",\"confirmationToken\":\"%s\",\"explanation\":\"%s\"}",
                actionType, targetNodeId, proposalToken, explanation
        );
    }

    @Tool("Xóa vĩnh viễn một tệp tin (Thao tác nhạy cảm: không thể phục hồi)")
    @SensitiveAction(actionType = "HARD_DELETE")
    public String permanentDelete(String nodeId, String confirmationToken) {
        // Will be intercepted by GuardrailAspect unless verified
        UUID userId = userContextManager.getCurrentUserId();
        log.info("Executing hard-delete: user={}, node={}", userId, nodeId);
        return String.format("Thành công: Đã xóa vĩnh viễn node %s", nodeId);
    }

    @Tool("Tạo liên kết chia sẻ công khai ra ngoài Internet (Thao tác nhạy cảm: rò rỉ dữ liệu)")
    @SensitiveAction(actionType = "PUBLIC_SHARE")
    public String createPublicShare(String nodeId, String confirmationToken) {
        // Will be intercepted by GuardrailAspect
        UUID userId = userContextManager.getCurrentUserId();
        log.info("Executing public share: user={}, node={}", userId, nodeId);
        return String.format("Thành công: Đã tạo public link cho node %s", nodeId);
    }
}
