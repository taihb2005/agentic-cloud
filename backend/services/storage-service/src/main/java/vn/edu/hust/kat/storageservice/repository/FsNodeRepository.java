package vn.edu.hust.kat.storageservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.hust.kat.storageservice.entity.FsNode;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FsNodeRepository extends JpaRepository<FsNode, Long>, JpaSpecificationExecutor<FsNode> {

    // Tìm node cha của user
    Optional<FsNode> findByUserIdAndParentIsNull(UUID userId);

    boolean existsByUserIdAndParentIsNull(UUID userId);

    boolean existsByUserIdAndId(UUID userId, Long nodeId);

    List<FsNode> findByUserIdAndParentId(UUID userId, Long parentId);

    Optional<FsNode> findByIdAndUserId(Long id, UUID userId);

    @Query("SELECT n FROM FsNode n WHERE n.user.id = :userId AND n.parent IS NULL AND n.name = :name")
    Optional<FsNode> findRootNodeByName(@Param("userId") UUID userId, @Param("name") String name);

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
}
