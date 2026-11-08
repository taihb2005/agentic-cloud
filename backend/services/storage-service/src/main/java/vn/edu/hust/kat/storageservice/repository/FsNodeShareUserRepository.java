package vn.edu.hust.kat.storageservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.hust.kat.storageservice.entity.FsNode;
import vn.edu.hust.kat.storageservice.entity.FsNodePermission;
import vn.edu.hust.kat.storageservice.entity.FsNodeShareUser;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FsNodeShareUserRepository extends JpaRepository<FsNodeShareUser, UUID> {

    List<FsNodeShareUser> findByNodeId(Long nodeId);

    List<FsNodeShareUser> findByNode(FsNode node);

    Optional<FsNodeShareUser> findByNodeIdAndUserId(Long nodeId, UUID userId);

    boolean existsByNodeIdAndUserId(Long nodeId, UUID userId);

    void deleteByNodeId(Long nodeId);

    void deleteByNodeIdAndUserId(Long nodeId, UUID userId);

    List<FsNodeShareUser> findByUserId(UUID userId);

    List<FsNodeShareUser> findByUserIdAndNodeIdIn(UUID userId, Collection<Long> nodeIds);

    @Query(value = """
        SELECT su FROM FsNodeShareUser su
        JOIN FETCH su.node n
        JOIN FETCH su.permission p
        LEFT JOIN FETCH n.user u
        LEFT JOIN FETCH n.share s
        WHERE su.userId = :userId
          AND n.deletedAt IS NULL
          AND (n.nodeStatus IS NULL OR n.nodeStatus != vn.edu.hust.kat.storageservice.entity.FsNode.Status.DELETED)
          AND (:isFolder IS NULL OR n.isFolder = :isFolder)
          AND (:permissionCode IS NULL OR p.code = :permissionCode)
          AND (:name IS NULL OR LOWER(n.name) LIKE LOWER(CONCAT('%', :name, '%')))
        """,
        countQuery = """
        SELECT COUNT(su) FROM FsNodeShareUser su
        JOIN su.node n
        JOIN su.permission p
        WHERE su.userId = :userId
          AND n.deletedAt IS NULL
          AND (n.nodeStatus IS NULL OR n.nodeStatus != vn.edu.hust.kat.storageservice.entity.FsNode.Status.DELETED)
          AND (:isFolder IS NULL OR n.isFolder = :isFolder)
          AND (:permissionCode IS NULL OR p.code = :permissionCode)
          AND (:name IS NULL OR LOWER(n.name) LIKE LOWER(CONCAT('%', :name, '%')))
        """)
    Page<FsNodeShareUser> findSharedWithMe(
            @Param("userId") UUID userId,
            @Param("isFolder") Boolean isFolder,
            @Param("permissionCode") FsNodePermission.PermissionCode permissionCode,
            @Param("name") String name,
            Pageable pageable
    );
}
