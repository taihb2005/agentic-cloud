package vn.edu.hust.kat.storageservice.entity;

import io.hypersistence.utils.hibernate.type.basic.PostgreSQLLTreeType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "fs_nodes",
        indexes = {
                @Index(name = "idx_node_browse", columnList = "user_id, parent_id, is_folder, name")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_fs_nodes_parent_id_name",
                        columnNames = {"parent_id", "name"}
                )
        })
public class FsNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private FsUser user;

    @Type(PostgreSQLLTreeType.class)
    @Column(name = "path", columnDefinition = "ltree")
    private String materializedPath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private FsNode parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    @Builder.Default
    private List<FsNode> children = new ArrayList<>();

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "is_folder", nullable = false)
    private Boolean isFolder;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_status", length = 20)
    private Status nodeStatus;

    @Column(name = "storage_bucket_name", length = 100)
    private String storageBucketName;

    @Column(name = "s3_object_key", length = 500)
    private String s3ObjectKey;

    @Column(name = "mime_type", length = 120)
    private String mimeType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "etag", length = 100)
    private String etag;

    @Column(name = "file_hash", length = 128)
    private String fileHash;

    @Column(name = "is_starred", nullable = false)
    private Boolean isStarred;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "delete_batch_id")
    private UUID deleteBatchId;

    @OneToOne(mappedBy = "node", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private FsShare share;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        if (isStarred == null) isStarred = false;
        if (isFolder == null) isFolder = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isDeleted() { return deletedAt != null && deleteBatchId != null; }

    public enum Status {
        ACTIVE, DELETED, PENDING, TEMPORARY
    }
}