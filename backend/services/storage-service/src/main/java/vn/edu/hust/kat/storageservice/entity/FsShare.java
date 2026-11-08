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
@Table(
        name = "fs_shares",
        uniqueConstraints = {
                @UniqueConstraint(name = "uc_fs_shares_node", columnNames = {"node_id"}),
                @UniqueConstraint(name = "uc_fs_shares_token", columnNames = {"share_token"})
        }
)
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

    @Enumerated(EnumType.STRING)
    @Column(name = "share_mode", length = 20)
    private ShareMode shareMode; // NONE, GROUP, PUBLIC

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id")
    private FsNodePermission permission;

    @Column(name = "share_token", length = 64, unique = true)
    private String shareToken;

    @Column(name = "is_password_protected", nullable = false)
    private Boolean isPasswordProtected;

    @Column(name = "password_hash")
    private String passwordHash;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private ShareStatus status; // NONE, ACTIVE, EXPIRED, REVOKED

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        if (isPublic == null) isPublic = false;
        if (isPasswordProtected == null) isPasswordProtected = false;
        if (accessCount == null) accessCount = 0;
        if (status == null) status = ShareStatus.NONE;
        if (shareMode == null) shareMode = ShareMode.NONE;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ShareStatus {
        NONE, ACTIVE, EXPIRED, REVOKED
    }

    public enum ShareMode {
        NONE, GROUP, USER_GROUP, PUBLIC, PUBLIC_LINK
    }
}
