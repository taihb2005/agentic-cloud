package vn.edu.hust.kat.userservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.edu.hust.kat.common.api.exception.ResourceNotFoundException;
import vn.edu.hust.kat.common.kafka.event.BaseEvent;
import vn.edu.hust.kat.common.kafka.payload.UserDeletedPayload;
import vn.edu.hust.kat.userservice.entity.User;
import vn.edu.hust.kat.userservice.grpc.StorageGrpcClient;
import vn.edu.hust.kat.userservice.kafka.UserEventProducer;
import vn.edu.hust.kat.userservice.repository.RefreshTokenRepository;
import vn.edu.hust.kat.userservice.repository.UserRepository;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private StorageGrpcClient storageGrpcClient;

    @Mock
    private UserEventProducer userEventProducer;

    @InjectMocks
    private UserService userService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .username("alice")
                .email("alice@example.com")
                .password("hashed-password")
                .build();
    }

    @Test
    void deleteUser_Success_HardDeletesAndPublishesEvent() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.deleteUser(userId);

        verify(refreshTokenRepository).deleteAllByUser(user);
        verify(userRepository).delete(user);
        verify(userRepository).flush();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<BaseEvent<UserDeletedPayload>> eventCaptor = ArgumentCaptor.forClass(BaseEvent.class);
        verify(userEventProducer).publishUserDeletedEvent(eventCaptor.capture());

        BaseEvent<UserDeletedPayload> capturedEvent = eventCaptor.getValue();
        assertNotNull(capturedEvent.getEventId());
        assertEquals("USER_DELETED", capturedEvent.getEventType());
        assertNotNull(capturedEvent.getTimestamp());
        assertNotNull(capturedEvent.getPayload());
        assertEquals(userId.toString(), capturedEvent.getPayload().getId());
        assertEquals("alice", capturedEvent.getPayload().getUsername());
        assertEquals("alice@example.com", capturedEvent.getPayload().getEmail());
    }

    @Test
    void deleteUser_NotFound_ThrowsResourceNotFoundException() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.deleteUser(userId));

        verify(userRepository, never()).delete(any());
        verify(userEventProducer, never()).publishUserDeletedEvent(any());
    }

    @Test
    void deleteCurrentUser_Success_DeletesAuthenticatedUser() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.deleteCurrentUser();

        verify(refreshTokenRepository).deleteAllByUser(user);
        verify(userRepository).delete(user);
        verify(userEventProducer).publishUserDeletedEvent(any());
    }
}
