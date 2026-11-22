package vn.edu.hust.kat.agentservice.tools;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.hust.kat.agentservice.context.UserContextManager;
import vn.edu.hust.kat.agentservice.dto.ActionProposalDto;
import vn.edu.hust.kat.agentservice.grpc.StorageGrpcClient;
import vn.edu.hust.kat.agentservice.guardrail.annotation.SensitiveAction;
import vn.edu.hust.kat.agentservice.service.ActionConfirmationService;
import vn.edu.hust.kat.proto.storage.v1.CreatePublicShareResponse;
import vn.edu.hust.kat.proto.storage.v1.PermanentDeleteNodeResponse;

import java.util.UUID;

/**
 * Tool descriptions and parameter prompts are loaded externally from {@code classpath:prompts/tool-prompts.yml}
 * via {@link vn.edu.hust.kat.agentservice.config.ToolPromptRegistry}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StorageSensitiveTools implements ITools {

    private static final Gson GSON = new Gson();

    private final UserContextManager userContextManager;
    private final StorageGrpcClient storageGrpcClient;
    private final ActionConfirmationService actionConfirmationService;

    @Tool
    public String createActionProposal(String actionType, String targetNodeId, String explanation) {
        UUID userId = userContextManager.getCurrentUserId();
        ActionProposalDto proposal = actionConfirmationService.createProposal(
                userId,
                actionType,
                targetNodeId,
                explanation
        );

        log.warn("Created sensitive action proposal (PENDING_CONFIRMATION): user={}, action={}, node={}, proposalId={}",
                userId, proposal.getActionType(), targetNodeId, proposal.getProposalId());

        return GSON.toJson(proposal);
    }

    @Tool
    @SensitiveAction(actionType = "HARD_DELETE")
    public String permanentDelete(String nodeId, String confirmationToken) {
        UUID userId = userContextManager.getCurrentUserId();
        log.info("Executing hard-delete via gRPC: user={}, node={}", userId, nodeId);

        PermanentDeleteNodeResponse response = storageGrpcClient.permanentDeleteNode(userId, nodeId);
        return String.format(
                "Thành công: Đã xóa vĩnh viễn node %s ('%s'), số mục đã xóa: %d",
                response.getNodeId(),
                response.getName(),
                response.getDeletedCount()
        );
    }

    @Tool
    @SensitiveAction(actionType = "PUBLIC_SHARE")
    public String createPublicShare(String nodeId, String confirmationToken) {
        UUID userId = userContextManager.getCurrentUserId();
        log.info("Executing public share via gRPC: user={}, node={}", userId, nodeId);

        CreatePublicShareResponse response = storageGrpcClient.createPublicShare(userId, nodeId, "VIEW");
        return String.format(
                "Thành công: Đã tạo public link cho node %s ('%s'): %s (shareToken=%s)",
                response.getNodeId(),
                response.getNodeName(),
                response.getSharedUrl(),
                response.getShareToken()
        );
    }
}
