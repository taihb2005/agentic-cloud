package vn.edu.hust.kat.authservice.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.Set;

@Getter
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long userId;
    private String name;
    private String email;
    private Set<String> roles;

    public static AuthResponse of(String accessToken, String refreshToken,
                                   Long userId, String name, String email, Set<String> roles) {
        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .tokenType("Bearer")
            .userId(userId)
            .name(name)
            .email(email)
            .roles(roles)
            .build();
    }
}
