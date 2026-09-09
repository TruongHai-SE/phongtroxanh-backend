package vn.phongtroxanh.backend.modules.review.infrastructure.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.phongtroxanh.backend.modules.review.domain.Review;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    List<Review> findByRoomIdOrderByCreatedAtDesc(UUID roomId);

    List<Review> findByRevieweeIdOrderByCreatedAtDesc(UUID revieweeId);

    Page<Review> findByStatus(String status, Pageable pageable);

    boolean existsByRentalContractIdAndReviewerId(UUID rentalContractId, UUID reviewerId);
}
