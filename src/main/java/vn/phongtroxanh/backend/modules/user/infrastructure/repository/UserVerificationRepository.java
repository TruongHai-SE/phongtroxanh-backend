package vn.phongtroxanh.backend.modules.user.infrastructure.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.phongtroxanh.backend.modules.user.domain.UserVerification;
import vn.phongtroxanh.backend.modules.user.domain.VerificationStatus;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserVerificationRepository extends JpaRepository<UserVerification, UUID> {
    Optional<UserVerification> findTopByUserIdOrderByCreatedAtDesc(UUID userId);
    Page<UserVerification> findByStatusOrderByCreatedAtAsc(VerificationStatus status, Pageable pageable);
    Page<UserVerification> findByStatus(VerificationStatus status, Pageable pageable);
}
