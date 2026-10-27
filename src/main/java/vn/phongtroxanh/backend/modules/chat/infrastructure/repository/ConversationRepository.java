package vn.phongtroxanh.backend.modules.chat.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.phongtroxanh.backend.modules.chat.domain.Conversation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    @Query("SELECT c FROM Conversation c WHERE (c.participantOneId = :userId OR c.participantTwoId = :userId) ORDER BY c.lastMessageAt DESC")
    List<Conversation> findByUser(@Param("userId") UUID userId);

    @Query("SELECT c FROM Conversation c WHERE ((c.participantOneId = :u1 AND c.participantTwoId = :u2) OR (c.participantOneId = :u2 AND c.participantTwoId = :u1)) AND (:roomId IS NULL OR c.roomId = :roomId) ORDER BY c.lastMessageAt DESC")
    List<Conversation> findListBetweenUsers(
            @Param("u1") UUID u1,
            @Param("u2") UUID u2,
            @Param("roomId") UUID roomId);

    default Optional<Conversation> findBetweenUsers(UUID u1, UUID u2, UUID roomId) {
        List<Conversation> list = findListBetweenUsers(u1, u2, roomId);
        return list == null || list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}
