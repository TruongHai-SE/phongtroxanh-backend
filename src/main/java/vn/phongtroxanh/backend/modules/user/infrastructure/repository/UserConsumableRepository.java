package vn.phongtroxanh.backend.modules.user.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.phongtroxanh.backend.modules.user.domain.UserConsumable;

import java.util.UUID;

@Repository
public interface UserConsumableRepository extends JpaRepository<UserConsumable, UUID> {

    @Modifying
    @Query("UPDATE UserConsumable u SET u.swipesLeft = u.swipesLeft - 1 WHERE u.userId = :userId AND u.swipesLeft > 0")
    int decrementSwipeAtomic(@Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE UserConsumable u SET u.superMatchesLeft = u.superMatchesLeft - 1 WHERE u.userId = :userId AND u.superMatchesLeft > 0")
    int decrementSuperMatchAtomic(@Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE UserConsumable u SET u.boostsLeft = u.boostsLeft - 1 WHERE u.userId = :userId AND u.boostsLeft > 0")
    int decrementBoostAtomic(@Param("userId") UUID userId);
}
