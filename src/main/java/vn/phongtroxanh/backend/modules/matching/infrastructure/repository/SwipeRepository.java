package vn.phongtroxanh.backend.modules.matching.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.phongtroxanh.backend.modules.matching.domain.Swipe;
import vn.phongtroxanh.backend.modules.matching.domain.SwipeAction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SwipeRepository extends JpaRepository<Swipe, UUID> {

    boolean existsBySwiperIdAndTargetId(UUID swiperId, UUID targetId);

    Optional<Swipe> findBySwiperIdAndTargetId(UUID swiperId, UUID targetId);

    @Query("SELECT s.targetId FROM Swipe s WHERE s.swiperId = :swiperId")
    List<UUID> findSwipedTargetIds(@Param("swiperId") UUID swiperId);

    @Query("SELECT s FROM Swipe s WHERE s.swiperId = :targetId AND s.targetId = :swiperId AND s.action IN (:actions)")
    Optional<Swipe> findReverseSwipe(
            @Param("swiperId") UUID swiperId,
            @Param("targetId") UUID targetId,
            @Param("actions") List<SwipeAction> actions);
}
