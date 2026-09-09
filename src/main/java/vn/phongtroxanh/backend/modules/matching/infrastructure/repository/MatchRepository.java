package vn.phongtroxanh.backend.modules.matching.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.phongtroxanh.backend.modules.matching.domain.Match;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchRepository extends JpaRepository<Match, UUID> {

    @Query("SELECT m FROM Match m WHERE (m.userAId = :userId OR m.userBId = :userId) AND m.status = 'MATCHED' ORDER BY m.createdAt DESC")
    List<Match> findActiveMatchesByUser(@Param("userId") UUID userId);

    @Query("SELECT m FROM Match m WHERE ((m.userAId = :u1 AND m.userBId = :u2) OR (m.userAId = :u2 AND m.userBId = :u1))")
    Optional<Match> findMatchBetween(@Param("u1") UUID u1, @Param("u2") UUID u2);
}
