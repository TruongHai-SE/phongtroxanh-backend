package vn.phongtroxanh.backend.modules.room.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.phongtroxanh.backend.modules.room.domain.SavedRoom;
import vn.phongtroxanh.backend.modules.room.domain.SavedRoomId;

import java.util.List;
import java.util.UUID;

@Repository
public interface SavedRoomRepository extends JpaRepository<SavedRoom, SavedRoomId> {
    List<SavedRoom> findByUserIdOrderByCreatedAtDesc(UUID userId);
    boolean existsByUserIdAndRoomId(UUID userId, UUID roomId);
    void deleteByUserIdAndRoomId(UUID userId, UUID roomId);
    long countByRoomIdIn(List<UUID> roomIds);
}
