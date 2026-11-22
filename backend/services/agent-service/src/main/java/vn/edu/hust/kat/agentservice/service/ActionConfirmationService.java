package vn.edu.hust.kat.agentservice.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import vn.edu.hust.kat.agentservice.dto.ActionProposalDto;
import vn.edu.hust.kat.agentservice.dto.ConfirmActionResponse;
import vn.edu.hust.kat.agentservice.entity.AgentAuditLog;
import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.common.api.exception.ResourceNotFoundException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActionConfirmationService {

    private static final String PROPOSAL_KEY_PREFIX = "agent:proposal:";
    private static final String TOKEN_KEY_PREFIX = "agent:confirmation_token:";
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(15);

    private final AuditService auditService;
    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    private final Map<String, ProposalRecord> fallbackProposals = new ConcurrentHashMap<>();
    private final Map<String, ConfirmationTokenRecord> fallbackTokens = new ConcurrentHashMap<>();
    private final Map<UUID, List<ActionProposalDto>> pendingProposalsByUser = new ConcurrentHashMap<>();

    public ActionProposalDto createProposal(UUID userId, String actionType, String targetNodeId, String explanation) {
        if (userId == null) {
            throw new IllegalStateException("No authenticated user found");
        }
        String normalizedAction = normalizeActionType(actionType);
        String cleanNodeId = targetNodeId != null ? targetNodeId.trim() : "";
        String cleanExplanation = explanation != null ? explanation.trim() : "";
        String proposalId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        ProposalRecord record = ProposalRecord.builder()
                .proposalId(proposalId)
                .userId(userId)
                .actionType(normalizedAction)
                .targetNodeId(cleanNodeId)
                .explanation(cleanExplanation)
                .status(ProposalRecord.Status.PENDING_CONFIRMATION)
                .confirmationToken(null)
                .createdAtEpochSecond(now.getEpochSecond())
                .expiresAtEpochSecond(now.plus(DEFAULT_TTL).getEpochSecond())
                .build();

        saveProposal(record);

        ActionProposalDto dto = ActionProposalDto.builder()
                .proposalId(proposalId)
                .actionType(normalizedAction)
                .targetNodeIds(cleanNodeId.isEmpty() ? Collections.emptyList() : List.of(cleanNodeId))
                .description(cleanExplanation)
                .confirmationToken(null)
                .requiresExplicitConfirmation(true)
                .build();

        pendingProposalsByUser
                .computeIfAbsent(userId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(dto);

        auditService.recordToolAction(
                userId,
                normalizedAction,
                "createActionProposal",
                cleanNodeId,
                "proposalId=" + proposalId + ", explanation=" + cleanExplanation,
                null,
                AgentAuditLog.Status.PENDING_CONFIRMATION,
                AgentAuditLog.HitlStatus.PENDING
        );

        log.info("Created pending action proposal {} for user={}, action={}, targetNode={}",
                proposalId, userId, normalizedAction, cleanNodeId);

        return dto;
    }

    public ConfirmActionResponse confirmProposal(UUID userId, String proposalId, boolean approved) {
        if (userId == null) {
            throw new IllegalStateException("No authenticated user found");
        }
        if (proposalId == null || proposalId.isBlank()) {
            throw new BadRequestException("Mã đề xuất (proposal_id) không được để trống");
        }

        String trimmedId = proposalId.trim();
        ProposalRecord proposal = loadProposal(trimmedId);
        if (proposal == null || proposal.isExpired()) {
            throw new ResourceNotFoundException("Không tìm thấy đề xuất xác nhận hoặc đề xuất đã hết hạn: " + trimmedId);
        }

        if (!userId.equals(proposal.getUserId())) {
            throw new AccessDeniedException("Bạn không có quyền xác nhận đề xuất của người dùng khác");
        }

        if (proposal.getStatus() != ProposalRecord.Status.PENDING_CONFIRMATION) {
            throw new BadRequestException("Đề xuất xác nhận này đã được xử lý trước đó (status=" + proposal.getStatus() + ")");
        }

        removePendingProposalDto(userId, trimmedId);

        if (!approved) {
            proposal.setStatus(ProposalRecord.Status.REJECTED);
            saveProposal(proposal);

            auditService.recordToolAction(
                    userId,
                    proposal.getActionType(),
                    "confirmAction",
                    proposal.getTargetNodeId(),
                    "proposalId=" + trimmedId + ", approved=false",
                    null,
                    AgentAuditLog.Status.BLOCKED,
                    AgentAuditLog.HitlStatus.REJECTED,
                    "Thao tác đã bị hủy bỏ theo yêu cầu của người dùng."
            );

            return ConfirmActionResponse.builder()
                    .proposalId(trimmedId)
                    .actionType(proposal.getActionType())
                    .targetNodeId(proposal.getTargetNodeId())
                    .approved(false)
                    .status(ProposalRecord.Status.REJECTED.name())
                    .confirmationToken(null)
                    .message("Thao tác đã bị hủy bỏ theo yêu cầu của bạn.")
                    .build();
        }

        String confirmationToken = UUID.randomUUID().toString();
        Instant now = Instant.now();

        proposal.setStatus(ProposalRecord.Status.APPROVED);
        proposal.setConfirmationToken(confirmationToken);
        saveProposal(proposal);

        ConfirmationTokenRecord tokenRecord = ConfirmationTokenRecord.builder()
                .confirmationToken(confirmationToken)
                .proposalId(trimmedId)
                .userId(userId)
                .actionType(proposal.getActionType())
                .targetNodeId(proposal.getTargetNodeId())
                .approved(true)
                .used(false)
                .createdAtEpochSecond(now.getEpochSecond())
                .expiresAtEpochSecond(now.plus(DEFAULT_TTL).getEpochSecond())
                .build();

        saveToken(tokenRecord);

        auditService.recordToolAction(
                userId,
                proposal.getActionType(),
                "confirmAction",
                proposal.getTargetNodeId(),
                "proposalId=" + trimmedId + ", confirmationToken=" + confirmationToken,
                null,
                AgentAuditLog.Status.SUCCESS,
                AgentAuditLog.HitlStatus.APPROVED
        );

        log.info("Approved proposal {} for user={}, generated confirmationToken={}", trimmedId, userId, confirmationToken);

        return ConfirmActionResponse.builder()
                .proposalId(trimmedId)
                .actionType(proposal.getActionType())
                .targetNodeId(proposal.getTargetNodeId())
                .approved(true)
                .status(ProposalRecord.Status.APPROVED.name())
                .confirmationToken(confirmationToken)
                .message("Đã xác nhận đồng ý. Hãy sử dụng confirmation_token này để Agent thực thi thao tác nhạy cảm.")
                .build();
    }

    public synchronized TokenValidationResult validateAndConsumeToken(
            UUID userId,
            String requiredActionType,
            String targetNodeId,
            String confirmationToken
    ) {
        if (userId == null) {
            return TokenValidationResult.invalid("Không tìm thấy thông tin xác thực người dùng.");
        }
        if (confirmationToken == null || confirmationToken.isBlank()) {
            return TokenValidationResult.invalid(
                    "Thiếu confirmationToken. Cần tạo đề xuất xác nhận (createActionProposal) và chờ người dùng đồng ý trước khi gọi tool."
            );
        }

        String trimmedToken = confirmationToken.trim();
        ConfirmationTokenRecord tokenRecord = loadToken(trimmedToken);

        if (tokenRecord == null) {
            ProposalRecord maybeProposal = loadProposal(trimmedToken);
            if (maybeProposal != null && maybeProposal.getStatus() == ProposalRecord.Status.PENDING_CONFIRMATION) {
                return TokenValidationResult.invalid(
                        "Mã truyền vào là proposalId đang ở trạng thái PENDING_CONFIRMATION (người dùng chưa đồng ý), không phải confirmationToken."
                );
            }
            return TokenValidationResult.invalid("confirmationToken không hợp lệ hoặc không tồn tại.");
        }

        if (tokenRecord.isExpired()) {
            return TokenValidationResult.invalid("confirmationToken đã hết hạn.");
        }

        if (!tokenRecord.isApproved()) {
            return TokenValidationResult.invalid("confirmationToken chưa được người dùng phê duyệt.");
        }

        if (tokenRecord.isUsed()) {
            return TokenValidationResult.invalid("confirmationToken đã được sử dụng trước đó (token chỉ có hiệu lực 1 lần).");
        }

        if (!userId.equals(tokenRecord.getUserId())) {
            return TokenValidationResult.invalid("confirmationToken không thuộc về người dùng hiện tại.");
        }

        String normalizedRequiredAction = normalizeActionType(requiredActionType);
        if (!normalizedRequiredAction.equalsIgnoreCase(tokenRecord.getActionType())) {
            return TokenValidationResult.invalid(
                    "confirmationToken được cấp cho hành động " + tokenRecord.getActionType()
                            + ", không hợp lệ cho hành động " + normalizedRequiredAction + "."
            );
        }

        if (tokenRecord.getTargetNodeId() != null && !tokenRecord.getTargetNodeId().isBlank()
                && targetNodeId != null && !targetNodeId.isBlank()
                && !tokenRecord.getTargetNodeId().trim().equals(targetNodeId.trim())) {
            return TokenValidationResult.invalid(
                    "confirmationToken chỉ hợp lệ cho node " + tokenRecord.getTargetNodeId()
                            + ", không hợp lệ cho node " + targetNodeId + "."
            );
        }

        tokenRecord.setUsed(true);
        saveToken(tokenRecord);

        return TokenValidationResult.valid();
    }

    public List<ActionProposalDto> drainPendingProposals(UUID userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        List<ActionProposalDto> list = pendingProposalsByUser.remove(userId);
        return list != null ? new ArrayList<>(list) : Collections.emptyList();
    }

    private void removePendingProposalDto(UUID userId, String proposalId) {
        List<ActionProposalDto> list = pendingProposalsByUser.get(userId);
        if (list != null) {
            list.removeIf(p -> proposalId.equals(p.getProposalId()));
        }
    }

    private String normalizeActionType(String actionType) {
        if (actionType == null || actionType.isBlank()) {
            return "SENSITIVE_ACTION";
        }
        String upper = actionType.trim().toUpperCase();
        if (upper.contains("DELETE") || upper.contains("TRASH")) {
            return "HARD_DELETE";
        }
        if (upper.contains("SHARE") || upper.contains("PUBLIC")) {
            return "PUBLIC_SHARE";
        }
        return upper;
    }

    private void saveProposal(ProposalRecord record) {
        fallbackProposals.put(record.getProposalId(), record);
        if (redisTemplate != null) {
            try {
                String json = jsonMapper.writeValueAsString(record);
                redisTemplate.opsForValue().set(PROPOSAL_KEY_PREFIX + record.getProposalId(), json, DEFAULT_TTL);
            } catch (Exception e) {
                log.debug("Redis unavailable while saving proposal {}, using in-memory fallback: {}",
                        record.getProposalId(), e.getMessage());
            }
        }
    }

    private ProposalRecord loadProposal(String proposalId) {
        if (redisTemplate != null) {
            try {
                String json = redisTemplate.opsForValue().get(PROPOSAL_KEY_PREFIX + proposalId);
                if (json != null && !json.isBlank()) {
                    ProposalRecord record = jsonMapper.readValue(json, ProposalRecord.class);
                    fallbackProposals.put(proposalId, record);
                    return record;
                }
            } catch (Exception e) {
                log.debug("Redis unavailable while loading proposal {}, using in-memory fallback: {}",
                        proposalId, e.getMessage());
            }
        }
        return fallbackProposals.get(proposalId);
    }

    private void saveToken(ConfirmationTokenRecord record) {
        fallbackTokens.put(record.getConfirmationToken(), record);
        if (redisTemplate != null) {
            try {
                String json = jsonMapper.writeValueAsString(record);
                redisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + record.getConfirmationToken(), json, DEFAULT_TTL);
            } catch (Exception e) {
                log.debug("Redis unavailable while saving confirmationToken {}, using in-memory fallback: {}",
                        record.getConfirmationToken(), e.getMessage());
            }
        }
    }

    private ConfirmationTokenRecord loadToken(String token) {
        if (redisTemplate != null) {
            try {
                String json = redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + token);
                if (json != null && !json.isBlank()) {
                    ConfirmationTokenRecord record = jsonMapper.readValue(json, ConfirmationTokenRecord.class);
                    fallbackTokens.put(token, record);
                    return record;
                }
            } catch (Exception e) {
                log.debug("Redis unavailable while loading confirmationToken {}, using in-memory fallback: {}",
                        token, e.getMessage());
            }
        }
        return fallbackTokens.get(token);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProposalRecord {
        private String proposalId;
        private UUID userId;
        private String actionType;
        private String targetNodeId;
        private String explanation;
        private Status status;
        private String confirmationToken;
        private long createdAtEpochSecond;
        private long expiresAtEpochSecond;

        public boolean isExpired() {
            return Instant.now().getEpochSecond() > expiresAtEpochSecond;
        }

        public enum Status {
            PENDING_CONFIRMATION,
            APPROVED,
            REJECTED
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfirmationTokenRecord {
        private String confirmationToken;
        private String proposalId;
        private UUID userId;
        private String actionType;
        private String targetNodeId;
        private boolean approved;
        private boolean used;
        private long createdAtEpochSecond;
        private long expiresAtEpochSecond;

        public boolean isExpired() {
            return Instant.now().getEpochSecond() > expiresAtEpochSecond;
        }
    }

    @Data
    @AllArgsConstructor
    public static class TokenValidationResult {
        private final boolean valid;
        private final String reason;

        public static TokenValidationResult valid() {
            return new TokenValidationResult(true, null);
        }

        public static TokenValidationResult invalid(String reason) {
            return new TokenValidationResult(false, reason);
        }
    }
}
