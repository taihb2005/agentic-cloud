package vn.edu.hust.kat.storageservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.hust.kat.storageservice.entity.FsNode;

import java.time.LocalDateTime;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FsNodeRepository extends JpaRepository<FsNode, Long>, JpaSpecificationExecutor<FsNode> {

    // Tìm node cha của user
    Optional<FsNode> findByUserIdAndParentIsNull(UUID userId);

    boolean existsByUserIdAndParentIsNull(UUID userId);

    boolean existsByUserIdAndId(UUID userId, Long nodeId);

    boolean existsByParentAndName(FsNode parent, String name);

    List<FsNode> findByUserIdAndParentId(UUID userId, Long parentId);

    Optional<FsNode> findByIdAndUserId(Long id, UUID userId);

    @Query("SELECT n FROM FsNode n WHERE n.user.id = :userId AND n.parent IS NULL AND n.name = :name")
    Optional<FsNode> findRootNodeByName(@Param("userId") UUID userId, @Param("name") String name);

    @Query(value = """
    SELECT EXISTS (
        SELECT 1
        FROM fs_nodes child
        JOIN fs_nodes parent
          ON parent.id = :parentNodeId
        WHERE child.id = :childNodeId
          AND child.path <@ parent.path
          AND child.id <> parent.id
    )
    """, nativeQuery = true)
    boolean isDescendant(
            @Param("childNodeId") Long childNodeId,
            @Param("parentNodeId") Long parentNodeId
    );

    @Query(value = """
    SELECT EXISTS (
        SELECT 1
        FROM fs_nodes ancestor
        JOIN fs_nodes descendant
          ON descendant.id = :descendantNodeId
        WHERE ancestor.id = :ancestorNodeId
          AND ancestor.path @> descendant.path
          AND ancestor.id <> descendant.id
    )
    """, nativeQuery = true)
    boolean isAncestor(
            @Param("ancestorNodeId") Long ancestorNodeId,
            @Param("descendantNodeId") Long descendantNodeId
    );

    @Query(value = """
        SELECT * FROM fs_nodes 
        WHERE user_id = :userId 
          AND path <@ CAST(:parentPath AS ltree) 
          AND nlevel(path) = nlevel(CAST(:parentPath AS ltree)) + 1 
          AND deleted_at IS NULL 
          AND (node_status IS NULL OR node_status != 'DELETED') 
          AND (CAST(:isFolder AS boolean) IS NULL OR is_folder = CAST(:isFolder AS boolean)) 
          AND (:name IS NULL OR name ILIKE CONCAT('%', CAST(:name AS text), '%')) 
          AND (:mimeType IS NULL OR mime_type = CAST(:mimeType AS varchar))
        """,
            countQuery = """
        SELECT count(*) FROM fs_nodes 
        WHERE user_id = :userId 
          AND path <@ CAST(:parentPath AS ltree) 
          AND nlevel(path) = nlevel(CAST(:parentPath AS ltree)) + 1 
          AND deleted_at IS NULL 
          AND (node_status IS NULL OR node_status != 'DELETED') 
          AND (CAST(:isFolder AS boolean) IS NULL OR is_folder = CAST(:isFolder AS boolean)) 
          AND (:name IS NULL OR name ILIKE CONCAT('%', CAST(:name AS text), '%')) 
          AND (:mimeType IS NULL OR mime_type = CAST(:mimeType AS varchar))
        """,
            nativeQuery = true)
    Page<FsNode> findChildNodesWithLtree(
            @Param("userId") UUID userId,
            @Param("parentPath") String parentPath,
            @Param("isFolder") Boolean isFolder,
            @Param("name") String name,
            @Param("mimeType") String mimeType,
            Pageable pageable
    );

    @Modifying
    @Query(value = """
        UPDATE fs_nodes
        SET parent_id = :dstNodeId,
            path = CAST(:dstParentPath AS ltree) || CAST(:srcNodeId AS ltree ),
            updated_at = LOCALTIMESTAMP
        WHERE id = :srcNodeId AND is_folder = false
    """, nativeQuery = true)
    int moveFile(
           @Param("srcNodeId") Long srcNodeId,
           @Param("dstParentId") Long dstParentId,
           @Param("dstParentPath") String dstParentPath
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE fs_nodes
        SET parent_id = CASE
                            WHEN id = :srcNodeId THEN :dstNodeId
                            ELSE parent_id
                        END,
            path = CAST(:dstNodePath AS ltree) || subpath(path, nlevel(CAST(:srcNodePath AS ltree)) - 1),
            updated_at = LOCALTIMESTAMP
        WHERE path <@ CAST(:srcNodePath AS ltree)
    """, nativeQuery = true)
    int moveFolderAndDescendents(
            @Param("srcNodeId") Long srcNodeId,
            @Param("dstNodeId") Long dstNodeId,
            @Param("srcNodePath") String srcNodePath,
            @Param("dstNodePath") String dstNodePath
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
    UPDATE fs_nodes
        SET node_status = 'DELETED',
            delete_batch_id = :deleteBatchId,
            deleted_at = LOCALTIMESTAMP,
            updated_at = LOCALTIMESTAMP
        WHERE user_id = :userId
          AND :nodePath IS NOT NULL
          AND :nodePath <> ''
          AND path <@ CAST(:nodePath AS ltree)
          AND deleted_at IS NULL
          AND delete_batch_id IS NULL
    """, nativeQuery = true)
    int softDeleteFolderAndDescendants(
            @Param("userId") UUID userId,
            @Param("nodePath") String nodePath,
            @Param("deleteBatchId") UUID deleteBatchId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE fs_nodes
        SET node_status = 'DELETED',
            delete_batch_id = :deleteBatchId,
            deleted_at = LOCALTIMESTAMP,
            updated_at = LOCALTIMESTAMP
        WHERE user_id = :userId
          AND id = :nodeId
          AND is_folder = false
          AND deleted_at IS NULL
          AND delete_batch_id IS NULL
    """, nativeQuery = true)
    int softDeleteFile(
            @Param("userId") UUID userId,
            @Param("nodeId") Long nodeId,
            @Param("deleteBatchId") UUID deleteBatchId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE fs_nodes
        SET node_status = 'ACTIVE',
            deleted_at = NULL,
            delete_batch_id = NULL,
            updated_at = LOCALTIMESTAMP
        WHERE user_id = :userId
          AND delete_batch_id = :deleteBatchId
          AND :nodePath IS NOT NULL
          AND :nodePath <> ''
          AND path <@ CAST(:nodePath AS ltree)
    """, nativeQuery = true)
    int restoreFolderAndDescendants(
            @Param("userId") UUID userId,
            @Param("nodePath") String nodePath,
            @Param("deleteBatchId") UUID deleteBatchId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE fs_nodes
        SET node_status = 'ACTIVE',
            deleted_at = NULL,
            delete_batch_id = NULL,
            updated_at = LOCALTIMESTAMP
        WHERE user_id = :userId
          AND id = :nodeId
          AND is_folder = false
          AND delete_batch_id = :deleteBatchId
        """, nativeQuery = true)
    int restoreFile(
            @Param("userId") UUID userId,
            @Param("nodeId") Long nodeId,
            @Param("deleteBatchId") UUID deleteBatchId
    );

    @Query(value = """
        SELECT EXISTS (
            SELECT 1 FROM fs_nodes
            WHERE user_id = :userId
              AND ((CAST(:parentId AS bigint) IS NULL AND parent_id IS NULL) OR parent_id = CAST(:parentId AS bigint))
              AND name = CAST(:name AS varchar)
              AND id != CAST(:nodeId AS bigint)
              AND deleted_at IS NULL
              AND (node_status IS NULL OR node_status != 'DELETED')
        )
        """, nativeQuery = true)
    boolean existsActiveNodeByParentAndName(
            @Param("userId") UUID userId,
            @Param("parentId") Long parentId,
            @Param("name") String name,
            @Param("nodeId") Long nodeId
    );
}
