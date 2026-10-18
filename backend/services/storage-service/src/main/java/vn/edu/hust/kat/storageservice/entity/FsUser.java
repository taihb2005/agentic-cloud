package vn.edu.hust.kat.storageservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "fs_users")
public class FsUser {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "storage_prefix", nullable = false)
    private String storagePrefix;

    @Column(name = "storage_bucket_name")
    private String storageBucketName;

    @Column(name = "storage_max_quota_bytes")
    private Long storageMaxQuotaBytes;

    @Column(name = "storage_current_quota_bytes")
    private Long storageCurrentQuotaBytes;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FsNode> nodes = new ArrayList<>();

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }
}
