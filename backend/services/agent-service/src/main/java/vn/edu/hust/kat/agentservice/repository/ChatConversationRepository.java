package vn.edu.hust.kat.agentservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.hust.kat.agentservice.entity.ChatConversation;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatConversationRepository extends JpaRepository<ChatConversation, UUID> {

    Optional<ChatConversation> findByIdAndUserIdAndStatusNot(
            UUID id,
            UUID userId,
            ChatConversation.Status excludedStatus
    );

    Page<ChatConversation> findByUserIdAndStatusNotOrderByLastMessageAtDescCreatedAtDesc(
            UUID userId,
            ChatConversation.Status excludedStatus,
            Pageable pageable
    );
}
