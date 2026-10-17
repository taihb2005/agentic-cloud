package vn.edu.hust.kat.storageservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "fs_shares")
public class FsShare {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // Quan hệ 1 - 1 với FS_NODES
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false, unique = true)
    private FsNode node;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "is_public", nullable = false)
    private Boolean isPublic;

    @Column(name = "shared_url", length = 500)
    private String sharedUrl;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "max_access_count")
    private Integer maxAccessCount;

    @Column(name = "access_count")
    private Integer accessCount;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        if (isPublic == null) isPublic = false;
        if (accessCount == null) accessCount = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
