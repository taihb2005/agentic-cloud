package vn.edu.hust.kat.userservice.service;

import vn.edu.hust.kat.common.api.exception.BadRequestException;
import vn.edu.hust.kat.common.api.exception.DuplicateResourceException;
import vn.edu.hust.kat.common.api.exception.ResourceNotFoundException;
import vn.edu.hust.kat.common.kafka.event.BaseEvent;
import vn.edu.hust.kat.common.kafka.payload.UserCreatedPayload;
import vn.edu.hust.kat.userservice.dto.request.LoginRequest;
import vn.edu.hust.kat.userservice.dto.request.RefreshTokenRequest;
import vn.edu.hust.kat.userservice.dto.request.RegisterRequest;
import vn.edu.hust.kat.userservice.dto.response.AuthResponse;
import vn.edu.hust.kat.userservice.entity.RefreshToken;
import vn.edu.hust.kat.userservice.entity.Role;
import vn.edu.hust.kat.userservice.entity.User;
import vn.edu.hust.kat.userservice.kafka.UserEventProducer;
import vn.edu.hust.kat.userservice.repository.RefreshTokenRepository;
import vn.edu.hust.kat.userservice.repository.RoleRepository;
import vn.edu.hust.kat.userservice.repository.UserRepository;
import vn.edu.hust.kat.userservice.security.JwtService;
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
    private final UserEventProducer userEventProducer;

    @Value("${app.jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username already registered: " + request.getUsername());
        }

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", "ROLE_USER"));

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(Set.of(userRole))
                .build();

        user = userRepository.save(user);
        log.info("New user registered: {}", user.getEmail());

        //Send user created event to kafka
        BaseEvent<UserCreatedPayload> event = BaseEvent.<UserCreatedPayload>builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("USER_CREATED")
                .timestamp(LocalDateTime.now())
                .payload(UserCreatedPayload.builder()
                        .id(user.getId().toString())
                        .email(user.getEmail())
                        .username(user.getUsername())
                        .build()
                )
                .build();

        userEventProducer.publishEvent(event);

        // Roles auto-embedded in JWT claims — no extra DB call needed on auth
        String accessToken = jwtService.generateToken(user, JwtService.TokenType.ACCESS_TOKEN);
        String refreshToken = createRefreshToken(user);

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", request.getUsername()));

        // Revoke old refresh tokens
        refreshTokenRepository.revokeAllByUser(user);

        String accessToken = jwtService.generateToken(user, JwtService.TokenType.ACCESS_TOKEN);
        String refreshToken = createRefreshToken(user);

        log.info("User logged in: {}", user.getEmail());
        return buildAuthResponse(user, accessToken, refreshToken);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken token = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));

        if (token.isRevoked() || jwtService.isTokenValid(token.getToken())) {
            throw new BadRequestException("Refresh token is expired or revoked. Please login again.");
        }

        User user = token.getUser();

        // Rotate refresh token
        token.setRevokesAt(LocalDateTime.now());
        refreshTokenRepository.save(token);

        String newAccessToken = jwtService.generateToken(user, JwtService.TokenType.ACCESS_TOKEN);
        String newRefreshToken = createRefreshToken(user);

        return buildAuthResponse(user, newAccessToken, newRefreshToken);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken).ifPresent(token -> {
            token.setRevokesAt(LocalDateTime.now());
            refreshTokenRepository.save(token);
        });
    }

    private String createRefreshToken(User user) {
        String refreshTokenValue = jwtService.generateToken(user, JwtService.TokenType.REFRESH_TOKEN);

        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenValue)
                .user(user)
                .build();
        refreshTokenRepository.save(refreshToken);
        return refreshTokenValue;
    }

    private AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {
        Set<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
        return AuthResponse.of(accessToken, refreshToken, user.getId(), user.getUsername(), user.getEmail(), roles);
    }
}