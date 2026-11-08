package vn.edu.hust.kat.storageservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.hust.kat.storageservice.entity.FsNode;
import vn.edu.hust.kat.storageservice.entity.FsShare;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FsShareRepository extends JpaRepository<FsShare, UUID> {

    Optional<FsShare> findByNodeId(Long nodeId);

    Optional<FsShare> findByNode(FsNode node);

    Optional<FsShare> findByShareToken(String shareToken);

    boolean existsByShareToken(String shareToken);

    void deleteByNodeId(Long nodeId);
}
