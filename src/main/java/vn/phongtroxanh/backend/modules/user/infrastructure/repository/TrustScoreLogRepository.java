package vn.phongtroxanh.backend.modules.user.infrastructure.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.phongtroxanh.backend.modules.user.domain.TrustScoreLog;

import java.util.List;
import java.util.UUID;

@Repository
public interface TrustScoreLogRepository extends JpaRepository<TrustScoreLog, UUID> {
    List<TrustScoreLog> findTop20ByUserIdOrderByCreatedAtDesc(UUID userId);
    Page<TrustScoreLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
