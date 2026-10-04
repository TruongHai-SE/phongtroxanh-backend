package vn.phongtroxanh.backend.modules.user.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.phongtroxanh.backend.modules.user.domain.UserConsumable;

import java.util.UUID;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface UserConsumableRepository extends JpaRepository<UserConsumable, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserConsumable u SET u.swipesLeft = u.swipesLeft - 1, " +
            "u.freeSwipesLeft = CASE WHEN u.freeSwipesLeft > 0 THEN u.freeSwipesLeft - 1 ELSE 0 END, " +
            "u.version = u.version + 1 WHERE u.userId = :userId AND u.swipesLeft > 0")
    int decrementSwipeAtomic(@Param("userId") UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE user_consumables c
            SET swipes_left = c.swipes_left - c.free_swipes_left + allowance.daily_limit,
                free_swipes_left = allowance.daily_limit, last_swipe_reset_at = :today, version = c.version + 1
            FROM (SELECT GREATEST(15, COALESCE(MAX((p.features ->> 'swipes_per_day')::integer), 15)) AS daily_limit
                  FROM subscriptions s JOIN package_plans p ON p.id = s.plan_id
                  JOIN users u ON u.id = s.user_id AND u.role = p.target_role
                  WHERE s.user_id = :userId AND p.target_role = 'TENANT' AND s.is_active = TRUE
                    AND s.start_date <= CURRENT_TIMESTAMP AND s.end_date > CURRENT_TIMESTAMP) allowance
            WHERE c.user_id = :userId AND (c.last_swipe_reset_at IS NULL OR c.last_swipe_reset_at < :today)
            """, nativeQuery = true)
    int resetDailySwipesAtomic(@Param("userId") UUID userId, @Param("today") LocalDate today);

    List<UserConsumable> findByProfileBoostExpiresAtAfter(Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserConsumable u SET u.boostsLeft = u.boostsLeft - 1, u.version = u.version + 1 WHERE u.userId = :userId AND u.boostsLeft > 0")
    int decrementBoostAtomic(@Param("userId") UUID userId);
}
