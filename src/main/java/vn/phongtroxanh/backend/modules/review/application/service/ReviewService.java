package vn.phongtroxanh.backend.modules.review.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.phongtroxanh.backend.common.exception.BadRequestException;
import vn.phongtroxanh.backend.common.exception.ConflictException;
import vn.phongtroxanh.backend.common.exception.ForbiddenException;
import vn.phongtroxanh.backend.common.exception.ResourceNotFoundException;
import vn.phongtroxanh.backend.common.security.SecurityUtils;
import vn.phongtroxanh.backend.common.storage.FileStoragePort;
import vn.phongtroxanh.backend.modules.rental.domain.Rental;
import vn.phongtroxanh.backend.modules.rental.domain.RentalStatus;
import vn.phongtroxanh.backend.modules.rental.infrastructure.repository.RentalRepository;
import vn.phongtroxanh.backend.modules.review.domain.Review;
import vn.phongtroxanh.backend.modules.review.domain.ReviewDispute;
import vn.phongtroxanh.backend.modules.review.domain.ReviewDisputeStatus;
import vn.phongtroxanh.backend.modules.review.infrastructure.repository.ReviewDisputeRepository;
import vn.phongtroxanh.backend.modules.review.infrastructure.repository.ReviewRepository;
import vn.phongtroxanh.backend.modules.review.presentation.dto.*;
import vn.phongtroxanh.backend.modules.user.domain.TrustScoreLog;
import vn.phongtroxanh.backend.modules.user.domain.User;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.TrustScoreLogRepository;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.UserRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewDisputeRepository reviewDisputeRepository;
    private final RentalRepository rentalRepository;
    private final UserRepository userRepository;
    private final TrustScoreLogRepository trustScoreLogRepository;
    private final FileStoragePort fileStoragePort;

    @Transactional
    public ReviewResponse createReview(CreateReviewRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        Rental rental = rentalRepository.findById(request.getRentalId())
                .orElseThrow(() -> new ResourceNotFoundException("RENTAL_NOT_FOUND", "Hợp đồng thuê không tồn tại"));

        if (rental.getStatus() != RentalStatus.CHECKED_IN && rental.getStatus() != RentalStatus.TERMINATED) {
            throw new BadRequestException("INVALID_RENTAL_STATUS", "Chỉ có thể đánh giá sau khi đã hoàn tất Check-in phòng trọ");
        }

        if (!rental.getTenantId().equals(currentUserId) && !rental.getLandlordId().equals(currentUserId)) {
            throw new ForbiddenException("BOLA_FORBIDDEN", "Bạn không thuộc hợp đồng thuê này để đánh giá");
        }

        UUID revieweeId = rental.getTenantId().equals(currentUserId) ? rental.getLandlordId() : rental.getTenantId();

        if (currentUserId.equals(revieweeId)) {
            throw new BadRequestException("SELF_REVIEW_NOT_ALLOWED", "Bạn không thể tự đánh giá chính mình");
        }

        if (reviewRepository.existsByRentalContractIdAndReviewerId(rental.getId(), currentUserId)) {
            throw new ConflictException("REVIEW_ALREADY_EXISTS", "Bạn đã gửi đánh giá cho hợp đồng thuê này rồi");
        }

        Review review = Review.builder()
                .rentalContractId(rental.getId())
                .reviewerId(currentUserId)
                .revieweeId(revieweeId)
                .roomId(rental.getRoomId())
                .rating(request.getRating())
                .comment(request.getComment())
                .tags(request.getTags())
                .images(request.getImages())
                .isVerifiedStay(true)
                .status("ACTIVE")
                .build();

        review = reviewRepository.save(review);

        // Adjust TrustScore
        if (request.getRating() >= 4) {
            adjustTrustScore(revieweeId, 5, "Nhận đánh giá tích cực (" + request.getRating() + " sao)");
        } else if (request.getRating() <= 2) {
            adjustTrustScore(revieweeId, -10, "Nhận đánh giá tiêu cực (" + request.getRating() + " sao)");
        }

        log.info("Created review {} from {} to {}", review.getId(), currentUserId, revieweeId);
        return mapToResponse(review);
    }

    public List<ReviewResponse> getReviewsForRoom(UUID roomId) {
        return reviewRepository.findByRoomIdOrderByCreatedAtDesc(roomId).stream()
                .filter(r -> !"REMOVED".equals(r.getStatus()))
                .map(this::mapToResponse)
                .toList();
    }

    public List<ReviewResponse> getReviewsForUser(UUID userId) {
        return reviewRepository.findByRevieweeIdOrderByCreatedAtDesc(userId).stream()
                .filter(r -> !"REMOVED".equals(r.getStatus()))
                .map(this::mapToResponse)
                .toList();
    }

    public ReviewResponse getReviewDetail(UUID id) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("REVIEW_NOT_FOUND", "Đánh giá không tồn tại"));
        return mapToResponse(review);
    }

    @Transactional
    public ReviewResponse submitDispute(UUID reviewId, SubmitDisputeRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("REVIEW_NOT_FOUND", "Đánh giá không tồn tại"));

        if (!review.getRevieweeId().equals(currentUserId)) {
            throw new ForbiddenException("BOLA_FORBIDDEN", "Chỉ người nhận đánh giá mới có quyền khiếu nại");
        }

        if ("UNDER_DISPUTE".equals(review.getStatus())) {
            throw new ConflictException("REVIEW_ALREADY_UNDER_DISPUTE", "Đánh giá này đang trong quá trình khiếu nại");
        }

        if ("REMOVED".equals(review.getStatus())) {
            throw new BadRequestException("REVIEW_ALREADY_REMOVED", "Đánh giá này đã bị gỡ bỏ, không thể khiếu nại");
        }

        review.setStatus("UNDER_DISPUTE");
        reviewRepository.save(review);

        ReviewDispute dispute = ReviewDispute.builder()
                .reviewId(reviewId)
                .appellantId(currentUserId)
                .reason(request.getReason())
                .evidenceImages(request.getEvidenceImages())
                .status("PENDING_REVIEW")
                .build();
        reviewDisputeRepository.save(dispute);

        log.info("Filed dispute for review {} by user {}", reviewId, currentUserId);
        return mapToResponse(review);
    }

    @Transactional
    public List<String> uploadEvidence(UUID reviewId, List<MultipartFile> files) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("REVIEW_NOT_FOUND", "Đánh giá không tồn tại"));

        UUID currentUserId = SecurityUtils.getCurrentUserId();
        if (!review.getRevieweeId().equals(currentUserId) && !SecurityUtils.hasRole("ROLE_ADMIN")) {
            throw new ForbiddenException("BOLA_FORBIDDEN", "Chỉ người nhận đánh giá mới có quyền tải lên bằng chứng khiếu nại");
        }

        List<String> urls = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    urls.add(fileStoragePort.uploadFile(file, "dispute-evidence"));
                }
            }
        }

        reviewDisputeRepository.findByReviewId(reviewId).ifPresent(dispute -> {
            List<String> existing = dispute.getEvidenceImages() != null ? new ArrayList<>(dispute.getEvidenceImages()) : new ArrayList<>();
            existing.addAll(urls);
            dispute.setEvidenceImages(existing);
            reviewDisputeRepository.save(dispute);
        });

        return urls;
    }

    @Transactional
    public ReviewResponse replyReview(UUID reviewId, ReplyReviewRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("REVIEW_NOT_FOUND", "Đánh giá không tồn tại"));

        UUID currentUserId = SecurityUtils.getCurrentUserId();
        if (!review.getRevieweeId().equals(currentUserId)) {
            throw new ForbiddenException("BOLA_FORBIDDEN", "Chỉ người nhận đánh giá mới có quyền phản hồi");
        }

        review.setLandlordReply(request.getReply());
        review.setRepliedAt(Instant.now());
        review = reviewRepository.save(review);

        return mapToResponse(review);
    }

    public Page<ReviewResponse> getPendingDisputes(int page, int limit) {
        int safeLimit = Math.min(Math.max(1, limit), 100);
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(safePage, safeLimit);

        Page<ReviewDispute> disputes = reviewDisputeRepository.findByStatus("PENDING_REVIEW", pageable);
        return disputes.map(d -> {
            Review r = reviewRepository.findById(d.getReviewId()).orElse(null);
            if (r != null) {
                return mapToResponse(r);
            }
            return ReviewResponse.builder()
                    .id(d.getReviewId())
                    .disputeStatus(ReviewDisputeStatus.PENDING)
                    .disputeReason(d.getReason())
                    .evidenceImages(d.getEvidenceImages())
                    .createdAt(d.getCreatedAt())
                    .build();
        });
    }

    private void adjustTrustScore(UUID userId, int delta, String reason) {
        userRepository.findById(userId).ifPresent(user -> {
            int newScore = Math.max(0, Math.min(100, user.getTrustScore() + delta));
            user.setTrustScore(newScore);
            userRepository.save(user);

            trustScoreLogRepository.save(TrustScoreLog.builder()
                    .userId(userId)
                    .delta(delta)
                    .finalScore(newScore)
                    .reason(reason)
                    .build());
        });
    }

    private ReviewResponse mapToResponse(Review review) {
        User reviewer = userRepository.findById(review.getReviewerId()).orElse(null);

        ReviewDisputeStatus disputeStatus = ReviewDisputeStatus.NONE;
        if ("UNDER_DISPUTE".equals(review.getStatus())) {
            disputeStatus = ReviewDisputeStatus.PENDING;
        } else if ("REMOVED".equals(review.getStatus())) {
            disputeStatus = ReviewDisputeStatus.RESOLVED_UPHELD;
        }

        return ReviewResponse.builder()
                .id(review.getId())
                .rentalId(review.getRentalContractId())
                .reviewerId(review.getReviewerId())
                .reviewerName(reviewer != null ? reviewer.getFullName() : "Người đánh giá")
                .reviewerAvatar(reviewer != null ? reviewer.getAvatarUrl() : null)
                .revieweeId(review.getRevieweeId())
                .roomId(review.getRoomId())
                .rating(review.getRating())
                .tags(review.getTags())
                .images(review.getImages())
                .comment(review.getComment())
                .disputeStatus(disputeStatus)
                .replyComment(review.getLandlordReply())
                .repliedAt(review.getRepliedAt())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
