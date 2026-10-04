package vn.phongtroxanh.backend.modules.room.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.phongtroxanh.backend.modules.room.domain.RoomSwipe;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomSwipeRepository extends JpaRepository<RoomSwipe, UUID> {

    Optional<RoomSwipe> findByUserIdAndRoomId(UUID userId, UUID roomId);

    boolean existsByUserIdAndRoomId(UUID userId, UUID roomId);

    @Query("SELECT rs.roomId FROM RoomSwipe rs WHERE rs.userId = :userId")
    List<UUID> findSwipedRoomIdsByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM RoomSwipe rs WHERE rs.userId = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
