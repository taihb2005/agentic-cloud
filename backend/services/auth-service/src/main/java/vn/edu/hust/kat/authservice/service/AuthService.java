package vn.edu.hust.kat.authservice.service;

import vn.edu.hust.kat.authservice.dto.request.LoginRequest;
import vn.edu.hust.kat.authservice.dto.request.RefreshTokenRequest;
import vn.edu.hust.kat.authservice.dto.request.RegisterRequest;
import vn.edu.hust.kat.authservice.dto.response.AuthResponse;
import vn.edu.hust.kat.authservice.entity.RefreshToken;
import vn.edu.hust.kat.authservice.entity.Role;
import vn.edu.hust.kat.authservice.entity.User;
import vn.edu.hust.kat.authservice.exception.BadRequestException;
import vn.edu.hust.kat.authservice.exception.DuplicateResourceException;
import vn.edu.hust.kat.authservice.exception.ResourceNotFoundException;
import vn.edu.hust.kat.authservice.repository.RefreshTokenRepository;
import vn.edu.hust.kat.authservice.repository.RoleRepository;
import vn.edu.hust.kat.authservice.repository.UserRepository;
import vn.edu.hust.kat.authservice.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Value("${app.jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already registered: " + request.getEmail());
        }

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", "ROLE_USER"));

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(Set.of(userRole))
                .build();

        user = userRepository.save(user);
        log.info("New user registered: {}", user.getEmail());

        // Roles auto-embedded in JWT claims — no extra DB call needed on auth
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = createRefreshToken(user);

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", request.getEmail()));

        // Revoke old refresh tokens
        refreshTokenRepository.revokeAllByUser(user);

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = createRefreshToken(user);

        log.info("User logged in: {}", user.getEmail());
        return buildAuthResponse(user, accessToken, refreshToken);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken token = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));

        if (token.isRevoked() || token.isExpired()) {
            throw new BadRequestException("Refresh token is expired or revoked. Please login again.");
        }

        User user = token.getUser();

        // Rotate refresh token
        token.setRevoked(true);
        refreshTokenRepository.save(token);

        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = createRefreshToken(user);

        return buildAuthResponse(user, newAccessToken, newRefreshToken);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    private String createRefreshToken(User user) {
        String tokenValue = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenValue)
                .user(user)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiry / 1000))
                .build();
        refreshTokenRepository.save(refreshToken);
        return tokenValue;
    }

    private AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {
        Set<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
        return AuthResponse.of(accessToken, refreshToken, user.getId(), user.getName(), user.getEmail(), roles);
    }
}