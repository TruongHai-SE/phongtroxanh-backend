package vn.phongtroxanh.backend.modules.review.infrastructure.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.phongtroxanh.backend.modules.review.domain.ReviewDispute;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewDisputeRepository extends JpaRepository<ReviewDispute, UUID> {

    Page<ReviewDispute> findByStatus(String status, Pageable pageable);

    Optional<ReviewDispute> findByReviewId(UUID reviewId);
}
