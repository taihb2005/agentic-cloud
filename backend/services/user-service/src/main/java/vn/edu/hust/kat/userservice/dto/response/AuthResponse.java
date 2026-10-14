package vn.edu.hust.kat.userservice.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.Set;
import java.util.UUID;

@Getter
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private UUID userId;
    private String username;
    private String email;
    private Set<String> roles;

    public static AuthResponse of(String accessToken, String refreshToken,
                                   UUID userId, String name, String email, Set<String> roles) {
        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .tokenType("Bearer")
            .userId(userId)
            .username(name)
            .email(email)
            .roles(roles)
            .build();
    }
}
