package vn.edu.hust.kat.userservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.net.InetAddress;
import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 512)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_info")
    private String deviceInfo;

    @Column(name = "ip_address")
    private InetAddress ipAddress;

    @Column(name = "revokes_at")
    private LocalDateTime revokesAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public boolean isRevoked() { return revokesAt != null; }
}
