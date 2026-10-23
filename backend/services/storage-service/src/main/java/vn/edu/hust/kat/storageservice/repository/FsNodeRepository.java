package vn.edu.hust.kat.storageservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.hust.kat.storageservice.entity.FsNode;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FsNodeRepository extends JpaRepository<FsNode, Long> {

    Optional<FsNode> findByUserIdAndParentIsNull(UUID userId);

    boolean existsByUserIdAndParentIsNull(UUID userId);

    List<FsNode> findByUserIdAndParentId(UUID userId, Long parentId);

    @Query("SELECT n FROM FsNode n WHERE n.user.id = :userId AND n.parent IS NULL AND n.name = :name")
    Optional<FsNode> findRootNodeByName(@Param("userId") UUID userId, @Param("name") String name);
}
