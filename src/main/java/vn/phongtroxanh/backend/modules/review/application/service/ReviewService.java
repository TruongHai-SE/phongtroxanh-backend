package vn.phongtroxanh.backend.modules.review.application.service;

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

import vn.phongtroxanh.backend.modules.review.domain.SystemFeedback;
import vn.phongtroxanh.backend.modules.review.infrastructure.repository.SystemFeedbackRepository;
import vn.phongtroxanh.backend.modules.review.presentation.dto.CreateSystemFeedbackRequest;
import vn.phongtroxanh.backend.modules.review.presentation.dto.SystemFeedbackResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewDisputeRepository reviewDisputeRepository;
    private final RentalRepository rentalRepository;
    private final UserRepository userRepository;
    private final TrustScoreLogRepository trustScoreLogRepository;
    private final FileStoragePort fileStoragePort;
    private final SystemFeedbackRepository systemFeedbackRepository;

    @Autowired
    public ReviewService(ReviewRepository reviewRepository,
                         ReviewDisputeRepository reviewDisputeRepository,
                         RentalRepository rentalRepository,
                         UserRepository userRepository,
                         TrustScoreLogRepository trustScoreLogRepository,
                         FileStoragePort fileStoragePort,
                         SystemFeedbackRepository systemFeedbackRepository) {
        this.reviewRepository = reviewRepository;
        this.reviewDisputeRepository = reviewDisputeRepository;
        this.rentalRepository = rentalRepository;
        this.userRepository = userRepository;
        this.trustScoreLogRepository = trustScoreLogRepository;
        this.fileStoragePort = fileStoragePort;
        this.systemFeedbackRepository = systemFeedbackRepository;
    }

    @Transactional
    public ReviewResponse createReview(CreateReviewRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        Rental rental = null;

        if (request.getRentalId() != null) {
            rental = rentalRepository.findById(request.getRentalId())
                    .orElseThrow(() -> new ResourceNotFoundException("RENTAL_NOT_FOUND", "Hợp đồng thuê không tồn tại"));
        } else if (request.getRoomId() != null) {
            rental = rentalRepository.findFirstByTenantIdAndRoomIdOrderByCreatedAtDesc(currentUserId, request.getRoomId())
                    .orElseThrow(() -> new ForbiddenException("RENTAL_REQUIRED", "Bạn cần có hợp đồng thuê phòng này trên hệ thống mới có thể viết đánh giá"));
        }

        if (rental == null) {
            throw new BadRequestException("MISSING_TARGET", "Vui lòng cung cấp rentalId hoặc roomId để đánh giá");
        }

        // Bắt buộc người thuê phải đã nhận phòng thực tế (đã Check-in thành công hoặc hợp đồng kết thúc đã ở)
        boolean hasCheckedIn = rental.getCheckedInAt() != null || rental.getStatus() == RentalStatus.CHECKED_IN;
        boolean wasCheckedInStay = rental.getStatus() == RentalStatus.TERMINATED && rental.getCheckedInAt() != null;
        if (!hasCheckedIn && !wasCheckedInStay) {
            throw new BadRequestException("CHECKIN_REQUIRED", "Bạn cần hoàn tất nhận phòng thực tế (Check-in) trước khi gửi đánh giá để đảm bảo tính khách quan");
        }

        boolean isVerifiedStay = true;

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
                .isVerifiedStay(isVerifiedStay)
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
        List<Review> list = reviewRepository.findByRoomIdOrderByCreatedAtDesc(roomId).stream()
                .filter(r -> !"REMOVED".equals(r.getStatus()))
                .toList();
        return mapReviewsBatch(list);
    }

    public List<ReviewResponse> getReviewsForUser(UUID userId) {
        List<Review> list = reviewRepository.findByRevieweeIdOrderByCreatedAtDesc(userId).stream()
                .filter(r -> !"REMOVED".equals(r.getStatus()))
                .toList();
        return mapReviewsBatch(list);
    }

    private List<ReviewResponse> mapReviewsBatch(List<Review> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return List.of();
        }
        Set<UUID> reviewerIds = new HashSet<>();
        for (Review r : reviews) {
            if (r.getReviewerId() != null) reviewerIds.add(r.getReviewerId());
        }
        Map<UUID, User> reviewerMap = new HashMap<>();
        if (!reviewerIds.isEmpty() && userRepository != null) {
            Iterable<User> fetched = userRepository.findAllById(reviewerIds);
            if (fetched != null) {
                fetched.forEach(u -> {
                    if (u != null && u.getId() != null) reviewerMap.put(u.getId(), u);
                });
            }
        }
        boolean batchLoaded = !reviewerMap.isEmpty();
        return reviews.stream()
                .map(r -> buildReviewResponse(r, batchLoaded
                        ? reviewerMap.get(r.getReviewerId())
                        : (userRepository != null ? userRepository.findById(r.getReviewerId()).orElse(null) : null)))
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

    @Transactional
    public ReviewResponse resolveDispute(UUID disputeId, ResolveDisputeRequest request) {
        ReviewDispute dispute = reviewDisputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("DISPUTE_NOT_FOUND", "Khiếu nại không tồn tại"));

        Review review = reviewRepository.findById(dispute.getReviewId())
                .orElseThrow(() -> new ResourceNotFoundException("REVIEW_NOT_FOUND", "Đánh giá không tồn tại"));

        UUID adminId = SecurityUtils.getCurrentUserId();
        dispute.setAdminNotes(request.getAdminNotes());
        dispute.setResolvedBy(adminId);
        dispute.setResolvedAt(Instant.now());

        if (Boolean.TRUE.equals(request.getApproved())) {
            // Admin chấp thuận khiếu nại: Gỡ đánh giá khỏi hiển thị và hoàn điểm uy tín
            review.setStatus("REMOVED");
            dispute.setStatus("RESOLVED_UPHELD");

            if (review.getRating() <= 2) {
                adjustTrustScore(review.getRevieweeId(), 10, "Được Admin chấp thuận khiếu nại (gỡ review oan)");
            }
        } else {
            // Admin bác bỏ khiếu nại: Giữ nguyên đánh giá
            review.setStatus("ACTIVE");
            dispute.setStatus("RESOLVED_DISMISSED");
        }

        reviewDisputeRepository.save(dispute);
        review = reviewRepository.save(review);

        log.info("Admin {} resolved dispute {} for review {}: approved={}", adminId, disputeId, review.getId(), request.getApproved());
        return mapToResponse(review);
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
        return buildReviewResponse(review, reviewer);
    }

    private ReviewResponse buildReviewResponse(Review review, User reviewer) {

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
                .isVerifiedStay(review.getIsVerifiedStay() != null ? review.getIsVerifiedStay() : true)
                .createdAt(review.getCreatedAt())
                .build();
    }

    public ReviewService(ReviewRepository reviewRepository,
                         ReviewDisputeRepository reviewDisputeRepository,
                         RentalRepository rentalRepository,
                         UserRepository userRepository,
                         TrustScoreLogRepository trustScoreLogRepository,
                         FileStoragePort fileStoragePort) {
        this(reviewRepository, reviewDisputeRepository, rentalRepository, userRepository, trustScoreLogRepository, fileStoragePort, null);
    }

    @Transactional
    public ReviewResponse updateReview(UUID reviewId, UpdateReviewRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("REVIEW_NOT_FOUND", "Đánh giá không tồn tại"));

        UUID currentUserId = SecurityUtils.getCurrentUserId();
        if (!review.getReviewerId().equals(currentUserId)) {
            throw new ForbiddenException("BOLA_FORBIDDEN", "Chỉ người viết đánh giá mới có quyền chỉnh sửa");
        }

        if ("UNDER_DISPUTE".equals(review.getStatus())) {
            throw new BadRequestException("REVIEW_UNDER_DISPUTE", "Đánh giá đang trong quá trình khiếu nại, không thể chỉnh sửa");
        }

        if ("REMOVED".equals(review.getStatus())) {
            throw new BadRequestException("REVIEW_REMOVED", "Đánh giá này đã bị gỡ bỏ, không thể chỉnh sửa");
        }

        int oldRating = review.getRating() != null ? review.getRating() : 5;
        int newRating = request.getRating();

        int oldScoreEffect = (oldRating >= 4) ? 5 : (oldRating <= 2 ? -10 : 0);
        int newScoreEffect = (newRating >= 4) ? 5 : (newRating <= 2 ? -10 : 0);
        int scoreDelta = newScoreEffect - oldScoreEffect;

        review.setRating(newRating);
        review.setComment(request.getComment().trim());
        if (request.getTags() != null) {
            review.setTags(request.getTags());
        }
        if (request.getImages() != null) {
            review.setImages(request.getImages());
        }
        review = reviewRepository.save(review);

        if (scoreDelta != 0) {
            adjustTrustScore(
                    review.getRevieweeId(),
                    scoreDelta,
                    "Cập nhật đánh giá từ " + oldRating + " sao thành " + newRating + " sao"
            );
        }

        log.info("User {} updated review {} with rating {} (scoreDelta={})", currentUserId, reviewId, newRating, scoreDelta);
        return mapToResponse(review);
    }

    @Transactional
    public SystemFeedbackResponse createSystemFeedback(CreateSystemFeedbackRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserIdSafely().orElse(null);
        SystemFeedback feedback = null;

        if (currentUserId != null && systemFeedbackRepository != null) {
            feedback = systemFeedbackRepository.findFirstByUserIdOrderByCreatedAtDesc(currentUserId).orElse(null);
        }

        if (feedback != null) {
            feedback.setRating(request.getRating());
            feedback.setCategory(request.getCategory() != null ? request.getCategory() : "app_experience");
            feedback.setComment(request.getComment().trim());
        } else {
            feedback = SystemFeedback.builder()
                    .userId(currentUserId)
                    .rating(request.getRating())
                    .category(request.getCategory() != null ? request.getCategory() : "app_experience")
                    .comment(request.getComment().trim())
                    .build();
        }

        if (systemFeedbackRepository != null) {
            feedback = systemFeedbackRepository.save(feedback);
        }

        String userName = "Người dùng ẩn danh";
        String userRole = "TENANT";
        if (currentUserId != null) {
            User u = userRepository.findById(currentUserId).orElse(null);
            if (u != null) {
                userName = u.getFullName() != null ? u.getFullName() : u.getEmail();
                userRole = u.getRole() != null ? u.getRole().name() : "TENANT";
            }
        }

        return SystemFeedbackResponse.builder()
                .id(feedback.getId())
                .userId(feedback.getUserId())
                .userName(userName)
                .userRole(userRole)
                .rating(feedback.getRating())
                .category(feedback.getCategory())
                .comment(feedback.getComment())
                .createdAt(feedback.getCreatedAt())
                .build();
    }

    public SystemFeedbackResponse getMySystemFeedback() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        if (systemFeedbackRepository == null) {
            throw new ResourceNotFoundException("FEEDBACK_NOT_FOUND", "Chưa có đánh giá nào");
        }
        SystemFeedback fb = systemFeedbackRepository.findFirstByUserIdOrderByCreatedAtDesc(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("FEEDBACK_NOT_FOUND", "Bạn chưa gửi góp ý nào"));

        String userName = "Người dùng ẩn danh";
        String userRole = "TENANT";
        if (userRepository != null) {
            User u = userRepository.findById(currentUserId).orElse(null);
            if (u != null) {
                userName = u.getFullName() != null ? u.getFullName() : u.getEmail();
                userRole = u.getRole() != null ? u.getRole().name() : "TENANT";
            }
        }

        return SystemFeedbackResponse.builder()
                .id(fb.getId())
                .userId(fb.getUserId())
                .userName(userName)
                .userRole(userRole)
                .rating(fb.getRating())
                .category(fb.getCategory())
                .comment(fb.getComment())
                .createdAt(fb.getCreatedAt())
                .build();
    }

    @Transactional
    public void deleteSystemFeedback(UUID id) {
        if (systemFeedbackRepository == null) return;
        SystemFeedback fb = systemFeedbackRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FEEDBACK_NOT_FOUND", "Không tìm thấy góp ý này"));
        systemFeedbackRepository.delete(fb);
        log.info("Admin deleted system feedback {}", id);
    }

    public Page<SystemFeedbackResponse> getSystemFeedbacks(int page, int limit) {
        int safeLimit = Math.min(Math.max(1, limit), 100);
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(safePage, safeLimit);

        if (systemFeedbackRepository == null) return Page.empty();

        Page<SystemFeedback> fbPage = systemFeedbackRepository.findAllByOrderByCreatedAtDesc(pageable);
        Set<UUID> userIds = new HashSet<>();
        fbPage.getContent().forEach(fb -> {
            if (fb.getUserId() != null) userIds.add(fb.getUserId());
        });
        Map<UUID, User> userMap = new HashMap<>();
        if (!userIds.isEmpty() && userRepository != null) {
            Iterable<User> fetched = userRepository.findAllById(userIds);
            if (fetched != null) {
                fetched.forEach(u -> {
                    if (u != null && u.getId() != null) userMap.put(u.getId(), u);
                });
            }
        }
        boolean batchLoaded = !userMap.isEmpty();

        return fbPage
                .map(fb -> {
                    String userName = "Người dùng ẩn danh";
                    String userRole = "TENANT";
                    if (fb.getUserId() != null && userRepository != null) {
                        User u = batchLoaded
                                ? userMap.get(fb.getUserId())
                                : userRepository.findById(fb.getUserId()).orElse(null);
                        if (u != null) {
                            userName = u.getFullName() != null ? u.getFullName() : u.getEmail();
                            userRole = u.getRole() != null ? u.getRole().name() : "TENANT";
                        }
                    }
                    return SystemFeedbackResponse.builder()
                            .id(fb.getId())
                            .userId(fb.getUserId())
                            .userName(userName)
                            .userRole(userRole)
                            .rating(fb.getRating())
                            .category(fb.getCategory())
                            .comment(fb.getComment())
                            .createdAt(fb.getCreatedAt())
                            .build();
                });
    }

    public List<SystemFeedbackResponse> getSystemFeedbacks() {
        return getSystemFeedbacks(0, 100).getContent();
    }
}

