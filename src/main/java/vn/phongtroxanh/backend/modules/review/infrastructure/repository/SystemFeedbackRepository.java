package vn.phongtroxanh.backend.modules.review.infrastructure.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.phongtroxanh.backend.modules.review.domain.SystemFeedback;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SystemFeedbackRepository extends JpaRepository<SystemFeedback, UUID> {
    Page<SystemFeedback> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<SystemFeedback> findAllByOrderByCreatedAtDesc();
    Optional<SystemFeedback> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);
}

