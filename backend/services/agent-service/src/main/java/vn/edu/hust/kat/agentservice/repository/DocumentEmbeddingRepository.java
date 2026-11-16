package vn.edu.hust.kat.agentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.hust.kat.agentservice.entity.DocumentEmbeddingEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentEmbeddingRepository extends JpaRepository<DocumentEmbeddingEntity, UUID> {
    List<DocumentEmbeddingEntity> findByUserIdAndNodeId(UUID userId, String nodeId);
    List<DocumentEmbeddingEntity> findByUserId(UUID userId);
}
