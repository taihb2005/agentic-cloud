package vn.edu.hust.kat.userservice.dto.response;

import vn.edu.hust.kat.userservice.entity.Role;
import vn.edu.hust.kat.userservice.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Builder
public class UserResponse {
    private UUID id;
    private String username;
    private String email;
    private Set<String> roles;
    private UserStorageResponse storage;
    private boolean enabled;
    private LocalDateTime createdAt;

    public static UserResponse from(User user, UserStorageResponse storage) {
        return UserResponse.builder()
            .id(user.getId())
            .username(user.getUsername())
            .email(user.getEmail())
            .roles(user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()))
            .enabled(user.isEnabled())
            .storage(storage)
            .createdAt(user.getCreatedAt())
            .build();
    }
}
