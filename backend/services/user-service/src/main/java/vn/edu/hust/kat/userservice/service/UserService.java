package vn.edu.hust.kat.userservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hust.kat.common.api.exception.ResourceNotFoundException;
import vn.edu.hust.kat.userservice.dto.response.UserResponse;
import vn.edu.hust.kat.userservice.dto.response.UserSearchResponse;
import vn.edu.hust.kat.userservice.dto.response.UserStorageResponse;
import vn.edu.hust.kat.userservice.entity.User;
import vn.edu.hust.kat.userservice.grpc.StorageGrpcClient;
import vn.edu.hust.kat.userservice.repository.UserRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final StorageGrpcClient storageGrpcClient;

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
    public void deleteUser(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User", "id", id);
        }
        userRepository.deleteById(id);
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
