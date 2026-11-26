package vn.edu.hust.kat.agentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.hust.kat.agentservice.entity.ChatMessage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findByConversationIdOrderBySequenceNumberAsc(UUID conversationId);

    List<ChatMessage> findByConversationIdAndTurnIdOrderBySequenceNumberAsc(UUID conversationId, UUID turnId);

    Optional<ChatMessage> findFirstByConversationIdAndTurnIdAndToolCallIdAndRole(
            UUID conversationId,
            UUID turnId,
            String toolCallId,
            ChatMessage.Role role
    );
}
