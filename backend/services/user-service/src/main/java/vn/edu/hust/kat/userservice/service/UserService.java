package vn.edu.hust.kat.userservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hust.kat.common.api.exception.ResourceNotFoundException;
import vn.edu.hust.kat.common.kafka.event.BaseEvent;
import vn.edu.hust.kat.common.kafka.payload.UserDeletedPayload;
import vn.edu.hust.kat.userservice.dto.response.UserResponse;
import vn.edu.hust.kat.userservice.dto.response.UserSearchResponse;
import vn.edu.hust.kat.userservice.dto.response.UserStorageResponse;
import vn.edu.hust.kat.userservice.entity.User;
import vn.edu.hust.kat.userservice.grpc.StorageGrpcClient;
import vn.edu.hust.kat.userservice.kafka.UserEventProducer;
import vn.edu.hust.kat.userservice.repository.RefreshTokenRepository;
import vn.edu.hust.kat.userservice.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final StorageGrpcClient storageGrpcClient;
    private final UserEventProducer userEventProducer;

    /**
     * Get the current authenticated user.
     * <p>
     * The GatewayAuthFilter sets the full User entity as the principal in
     * SecurityContext, so we can extract it directly without an extra DB query.
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        User user = getAuthenticatedUser();
        UserStorageResponse userStorageResponse = storageGrpcClient.getUserStorage(user.getId());

        return UserResponse.from(user, userStorageResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        UserStorageResponse userStorageResponse = storageGrpcClient.getUserStorage(user.getId());

        return UserResponse.from(user, userStorageResponse);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(user -> {
            UserStorageResponse userStorageResponse = storageGrpcClient.getUserStorage(user.getId());
            return UserResponse.from(user, userStorageResponse);
        });
    }

    @Transactional(readOnly = true)
    public List<UserSearchResponse> searchUsers(String query, int limit) {
        UUID callerId = null;
        try {
            User caller = getAuthenticatedUser();
            if (caller != null) {
                callerId = caller.getId();
            }
        } catch (Exception ignored) {
        }

        int pageSize = Math.max(1, Math.min(limit, 50));
        Pageable pageable = PageRequest.of(0, pageSize);
        String searchPattern = (query != null) ? query.trim() : "";

        return userRepository.searchUsers(searchPattern, callerId, pageable)
                .stream()
                .map(UserSearchResponse::from)
                .toList();
    }

    @Transactional
    public void deleteCurrentUser() {
        User user = getAuthenticatedUser();
        deleteUser(user.getId());
    }

    @Transactional
    public void deleteUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        String userIdStr = user.getId().toString();
        String username = user.getUsername();
        String email = user.getEmail();

        refreshTokenRepository.deleteAllByUser(user);
        userRepository.delete(user);
        userRepository.flush();

        log.info("Hard deleted user: id={}, username={}, email={}", userIdStr, username, email);

        BaseEvent<UserDeletedPayload> event = BaseEvent.<UserDeletedPayload>builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("USER_DELETED")
                .timestamp(LocalDateTime.now())
                .payload(UserDeletedPayload.builder()
                        .id(userIdStr)
                        .username(username)
                        .email(email)
                        .build()
                )
                .build();

        userEventProducer.publishUserDeletedEvent(event);
    }

    /**
     * Extract the authenticated User entity from the SecurityContext.
     * The principal is set as a User object by GatewayAuthFilter.
     */
    public User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User)) {
            throw new ResourceNotFoundException("User", "authentication", "No authenticated user found");
        }
        return (User) authentication.getPrincipal();
    }
}
