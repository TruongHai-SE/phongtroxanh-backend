package vn.phongtroxanh.backend.modules.admin.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongtroxanh.backend.common.exception.BadRequestException;
import vn.phongtroxanh.backend.common.exception.ResourceNotFoundException;
import vn.phongtroxanh.backend.common.security.SecurityUtils;
import vn.phongtroxanh.backend.modules.admin.domain.*;
import vn.phongtroxanh.backend.modules.admin.infrastructure.repository.ReportRepository;
import vn.phongtroxanh.backend.modules.admin.infrastructure.repository.SystemAuditLogRepository;
import vn.phongtroxanh.backend.modules.admin.presentation.dto.*;
import vn.phongtroxanh.backend.modules.matching.infrastructure.repository.MatchRepository;
import vn.phongtroxanh.backend.modules.monetization.domain.PaymentStatus;
import vn.phongtroxanh.backend.modules.monetization.domain.PaymentTransaction;
import vn.phongtroxanh.backend.modules.monetization.infrastructure.repository.PaymentTransactionRepository;
import vn.phongtroxanh.backend.modules.rental.domain.RentalStatus;
import vn.phongtroxanh.backend.modules.rental.infrastructure.repository.RentalRepository;
import vn.phongtroxanh.backend.modules.review.domain.Review;
import vn.phongtroxanh.backend.modules.review.domain.ReviewDispute;
import vn.phongtroxanh.backend.modules.review.domain.ReviewDisputeStatus;
import vn.phongtroxanh.backend.modules.review.infrastructure.repository.ReviewDisputeRepository;
import vn.phongtroxanh.backend.modules.review.infrastructure.repository.ReviewRepository;
import vn.phongtroxanh.backend.modules.room.domain.Room;
import vn.phongtroxanh.backend.modules.room.domain.RoomStatus;
import vn.phongtroxanh.backend.modules.room.infrastructure.repository.RoomRepository;
import vn.phongtroxanh.backend.modules.user.domain.*;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final UserVerificationRepository userVerificationRepository;
    private final TrustScoreLogRepository trustScoreLogRepository;
    private final RoomRepository roomRepository;
    private final MatchRepository matchRepository;
    private final RentalRepository rentalRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewDisputeRepository reviewDisputeRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ReportRepository reportRepository;
    private final SystemAuditLogRepository systemAuditLogRepository;

    public AdminDashboardResponse getDashboard() {
        long totalUsers = userRepository.count();
        long totalLandlords = userRepository.findByRole(UserRole.LANDLORD, Pageable.unpaged()).getTotalElements();
        long totalTenants = userRepository.findByRole(UserRole.TENANT, Pageable.unpaged()).getTotalElements();
        long totalRooms = roomRepository.count();
        long activeRooms = roomRepository.findAll().stream().filter(r -> r.getStatus() == RoomStatus.AVAILABLE).count();
        long totalMatches = matchRepository.count();
        long totalRentals = rentalRepository.count();
        long activeRentals = rentalRepository.findAll().stream().filter(r -> r.getStatus() == RentalStatus.CHECKED_IN).count();
        long pendingKyc = userVerificationRepository.findByStatus(VerificationStatus.PENDING, Pageable.unpaged()).getTotalElements();
        long pendingDisputes = reviewDisputeRepository.findByStatus("PENDING_REVIEW", Pageable.unpaged()).getTotalElements();

        BigDecimal totalRevenue = paymentTransactionRepository.findAll().stream()
                .filter(t -> t.getStatus() == PaymentStatus.SUCCESS)
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return AdminDashboardResponse.builder()
                .totalUsers(totalUsers)
                .totalLandlords(totalLandlords)
                .totalTenants(totalTenants)
                .totalRooms(totalRooms)
                .activeRooms(activeRooms)
                .totalMatches(totalMatches)
                .totalRentals(totalRentals)
                .activeRentals(activeRentals)
                .pendingKycCount(pendingKyc)
                .pendingDisputesCount(pendingDisputes)
                .totalRevenue(totalRevenue)
                .build();
    }

    public List<KycAuditItemDTO> getPendingKycList() {
        List<UserVerification> list = userVerificationRepository.findByStatus(VerificationStatus.PENDING, Pageable.unpaged()).getContent();
        List<KycAuditItemDTO> result = new ArrayList<>();

        for (UserVerification v : list) {
            User user = userRepository.findById(v.getUserId()).orElse(null);
            result.add(KycAuditItemDTO.builder()
                    .verificationId(v.getId())
                    .userId(v.getUserId())
                    .userFullName(user != null ? user.getFullName() : "N/A")
                    .userEmail(user != null ? user.getEmail() : "N/A")
                    .userPhone(user != null ? user.getPhoneNumber() : null)
                    .userRole(user != null ? user.getRole() : null)
                    .idCardNumberDecrypted(v.getIdCardNumber()) // auto-decrypted via AES AttributeConverter
                    .idCardFrontUrl(v.getIdCardFrontUrl())
                    .idCardBackUrl(v.getIdCardBackUrl())
                    .status(v.getStatus())
                    .createdAt(v.getCreatedAt())
                    .build());
        }
        return result;
    }

    @Transactional
    public void approveKyc(UUID verificationId) {
        UserVerification verification = userVerificationRepository.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("VERIFICATION_NOT_FOUND", "Không tìm thấy hồ sơ xác thực"));

        verification.setStatus(VerificationStatus.APPROVED);
        verification.setReviewedAt(Instant.now());
        userVerificationRepository.save(verification);

        // Update User TrustScore (+30 points) and mark verified
        userRepository.findById(verification.getUserId()).ifPresent(user -> {
            user.setIsVerified(true);
            int oldScore = user.getTrustScore();
            int newScore = Math.min(100, oldScore + 30);
            user.setTrustScore(newScore);
            userRepository.save(user);

            trustScoreLogRepository.save(TrustScoreLog.builder()
                    .userId(user.getId())
                    .delta(30)
                    .finalScore(newScore)
                    .reason("Xác thực định danh CCCD thành công (+30 điểm)")
                    .build());
        });

        recordAuditLog("APPROVE_KYC", "USER_VERIFICATION", verificationId, null, "APPROVED");
        log.info("Approved KYC verification {}", verificationId);
    }

    @Transactional
    public void rejectKyc(UUID verificationId, KycRejectRequest request) {
        UserVerification verification = userVerificationRepository.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("VERIFICATION_NOT_FOUND", "Không tìm thấy hồ sơ xác thực"));

        verification.setStatus(VerificationStatus.REJECTED);
        verification.setRejectionReason(request.getReason());
        verification.setReviewedAt(Instant.now());
        userVerificationRepository.save(verification);

        recordAuditLog("REJECT_KYC", "USER_VERIFICATION", verificationId, null, "REJECTED: " + request.getReason());
        log.info("Rejected KYC verification {} with reason {}", verificationId, request.getReason());
    }

    public Page<AdminUserResponse> getUsers(UserRole role, UserStatus status, int page, int limit) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, limit), 100));
        Page<User> users;
        if (role != null) {
            users = userRepository.findByRole(role, pageable);
        } else {
            users = userRepository.findAll(pageable);
        }

        return users.map(u -> AdminUserResponse.builder()
                .id(u.getId())
                .email(u.getEmail())
                .phoneNumber(u.getPhoneNumber())
                .fullName(u.getFullName())
                .avatarUrl(u.getAvatarUrl())
                .role(u.getRole())
                .status(u.getStatus())
                .trustScore(u.getTrustScore())
                .isVerified(u.getIsVerified())
                .createdAt(u.getCreatedAt())
                .build());
    }

    @Transactional
    public void updateUserStatus(UUID userId, UpdateUserStatusRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy người dùng"));

        UserStatus prevStatus = user.getStatus();
        user.setStatus(request.getStatus());
        userRepository.save(user);

        if (request.getStatus() == UserStatus.LOCKED) {
            trustScoreLogRepository.save(TrustScoreLog.builder()
                    .userId(userId)
                    .delta(-user.getTrustScore())
                    .finalScore(0)
                    .reason("Tài khoản bị khóa bởi Quản trị viên")
                    .build());
            user.setTrustScore(0);
            userRepository.save(user);
        }

        recordAuditLog("UPDATE_USER_STATUS", "USER", userId, prevStatus.name(), request.getStatus().name());
        log.info("Updated user {} status to {}", userId, request.getStatus());
    }

    public Page<AdminRoomResponse> getRooms(RoomStatus status, int page, int limit) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, limit), 100));
        Page<Room> rooms;
        if (status != null) {
            rooms = roomRepository.findByStatus(status, pageable);
        } else {
            rooms = roomRepository.findAll(pageable);
        }

        return rooms.map(r -> AdminRoomResponse.builder()
                .id(r.getId())
                .landlordId(r.getLandlordId())
                .title(r.getTitle())
                .description(r.getDescription())
                .roomType(r.getRoomType())
                .price(r.getPrice())
                .depositAmount(r.getDepositAmount())
                .areaSqm(r.getAreaSqm())
                .floorNumber(r.getFloorNumber())
                .maxOccupants(r.getMaxOccupants())
                .addressStreet(r.getAddressStreet())
                .district(r.getDistrict())
                .city(r.getCity())
                .latitude(r.getLatitude())
                .longitude(r.getLongitude())
                .status(r.getStatus())
                .isVerified(r.getIsVerified())
                .isBoosted(r.getIsBoosted())
                .viewCount(r.getViewCount())
                .createdAt(r.getCreatedAt())
                .build());
    }

    @Transactional
    public void verifyRoom(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("ROOM_NOT_FOUND", "Không tìm thấy phòng"));

        room.setIsVerified(true);
        roomRepository.save(room);
        recordAuditLog("VERIFY_ROOM", "ROOM", roomId, null, "VERIFIED");
        log.info("Admin verified room {}", roomId);
    }

    @Transactional
    public void takeDownRoom(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("ROOM_NOT_FOUND", "Không tìm thấy phòng"));

        room.setStatus(RoomStatus.HIDDEN);
        roomRepository.save(room);
        recordAuditLog("TAKE_DOWN_ROOM", "ROOM", roomId, null, "HIDDEN");
        log.info("Admin took down room {}", roomId);
    }

    public Page<AdminReviewDisputeResponse> getDisputes(int page, int limit) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, limit), 100));
        Page<ReviewDispute> disputes = reviewDisputeRepository.findByStatus("PENDING_REVIEW", pageable);

        return disputes.map(d -> AdminReviewDisputeResponse.builder()
                .id(d.getId())
                .reviewId(d.getReviewId())
                .appellantId(d.getAppellantId())
                .reason(d.getReason())
                .evidenceImages(d.getEvidenceImages())
                .status(d.getStatus())
                .adminNotes(d.getAdminNotes())
                .resolvedBy(d.getResolvedBy())
                .createdAt(d.getCreatedAt())
                .resolvedAt(d.getResolvedAt())
                .build());
    }

    @Transactional
    public void resolveDispute(UUID disputeId, ResolveDisputeRequest request) {
        ReviewDispute dispute = reviewDisputeRepository.findById(disputeId)
                .orElseGet(() -> reviewDisputeRepository.findByReviewId(disputeId).orElse(null));

        if (dispute == null) {
            throw new ResourceNotFoundException("DISPUTE_NOT_FOUND", "Không tìm thấy khiếu nại");
        }

        Review review = reviewRepository.findById(dispute.getReviewId())
                .orElseThrow(() -> new ResourceNotFoundException("REVIEW_NOT_FOUND", "Không tìm thấy đánh giá"));

        UUID currentAdminId = SecurityUtils.getCurrentUserId();

        if (request.getDecision() == ReviewDisputeStatus.RESOLVED_REMOVED) {
            review.setStatus("REMOVED");
            dispute.setStatus("RESOLVED_UPHELD");
            dispute.setResolvedBy(currentAdminId);
            dispute.setResolvedAt(Instant.now());
            dispute.setAdminNotes(request.getAdminNotes());
            reviewDisputeRepository.save(dispute);

            // Restore TrustScore to reviewee
            userRepository.findById(review.getRevieweeId()).ifPresent(u -> {
                int restoredScore = Math.min(100, u.getTrustScore() + 10);
                u.setTrustScore(restoredScore);
                userRepository.save(u);

                trustScoreLogRepository.save(TrustScoreLog.builder()
                        .userId(u.getId())
                        .delta(10)
                        .finalScore(restoredScore)
                        .reason("Admin chấp thuận khiếu nại và gỡ bỏ đánh giá ác ý")
                        .build());
            });
        } else {
            review.setStatus("ACTIVE");
            dispute.setStatus("RESOLVED_REJECTED");
            dispute.setResolvedBy(currentAdminId);
            dispute.setResolvedAt(Instant.now());
            dispute.setAdminNotes(request.getAdminNotes());
            reviewDisputeRepository.save(dispute);
        }

        reviewRepository.save(review);
        recordAuditLog("RESOLVE_DISPUTE", "REVIEW_DISPUTE", dispute.getId(), null, request.getDecision().name());
        log.info("Admin resolved dispute {} with decision {}", dispute.getId(), request.getDecision());
    }

    public Page<AdminPaymentTransactionResponse> getTransactions(int page, int limit) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, limit), 100));
        Page<PaymentTransaction> txs = paymentTransactionRepository.findAllByOrderByCreatedAtDesc(pageable);

        return txs.map(t -> AdminPaymentTransactionResponse.builder()
                .id(t.getId())
                .userId(t.getUserId())
                .itemType(t.getItemType())
                .itemName(t.getItemName())
                .amount(t.getAmount())
                .paymentMethod(t.getPaymentMethod())
                .status(t.getStatus())
                .idempotencyKey(t.getIdempotencyKey())
                .gatewayOrderId(t.getGatewayOrderId())
                .qrCodeUrl(t.getQrCodeUrl())
                .qrExpiredAt(t.getQrExpiredAt())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build());
    }

    public Page<AdminReportResponse> getReports(ReportStatus status, ReportSeverity severity, int page, int limit) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, limit), 100));
        Page<Report> reports;
        if (status != null && severity != null) {
            reports = reportRepository.findByStatusAndSeverityOrderByCreatedAtDesc(status, severity, pageable);
        } else if (status != null) {
            reports = reportRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else if (severity != null) {
            reports = reportRepository.findBySeverityOrderByCreatedAtDesc(severity, pageable);
        } else {
            reports = reportRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        return reports.map(this::mapToReportResponse);
    }

    public AdminReportResponse getReportDetail(UUID id) {
        Report r = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("REPORT_NOT_FOUND", "Không tìm thấy báo cáo vi phạm"));
        return mapToReportResponse(r);
    }

    @Transactional
    public AdminReportResponse handleReportAction(UUID id, AdminReportActionRequest request) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("REPORT_NOT_FOUND", "Không tìm thấy báo cáo vi phạm"));

        UUID currentAdminId = SecurityUtils.getCurrentUserId();
        report.setHandlerId(currentAdminId);

        String action = request.getAction().toLowerCase();
        switch (action) {
            case "resolve" -> report.setStatus(ReportStatus.RESOLVED);
            case "dismiss" -> report.setStatus(ReportStatus.DISMISSED);
            case "warn" -> {
                report.setStatus(ReportStatus.RESOLVED);
                if ("USER".equalsIgnoreCase(report.getTargetType())) {
                    userRepository.findById(report.getTargetId()).ifPresent(u -> {
                        u.setStatus(UserStatus.WARNED);
                        userRepository.save(u);
                    });
                }
            }
            case "ban" -> {
                report.setStatus(ReportStatus.RESOLVED);
                if ("USER".equalsIgnoreCase(report.getTargetType())) {
                    userRepository.findById(report.getTargetId()).ifPresent(u -> {
                        u.setStatus(UserStatus.LOCKED);
                        userRepository.save(u);
                    });
                }
            }
            case "remove_content" -> {
                report.setStatus(ReportStatus.RESOLVED);
                if ("ROOM".equalsIgnoreCase(report.getTargetType())) {
                    roomRepository.findById(report.getTargetId()).ifPresent(r -> {
                        r.setStatus(RoomStatus.HIDDEN);
                        roomRepository.save(r);
                    });
                }
            }
            default -> throw new BadRequestException("INVALID_ACTION", "Hành động không hợp lệ: " + action);
        }

        report = reportRepository.save(report);
        recordAuditLog("HANDLE_REPORT", "REPORT", report.getId(), null, action + ": " + request.getNote());
        return mapToReportResponse(report);
    }

    public Page<AdminAuditLogResponse> getAuditLogs(int page, int limit) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, limit), 100));
        Page<SystemAuditLog> logs = systemAuditLogRepository.findAllByOrderByCreatedAtDesc(pageable);

        return logs.map(l -> AdminAuditLogResponse.builder()
                .id(l.getId())
                .actorId(l.getActorId())
                .action(l.getAction())
                .targetEntity(l.getTargetEntity())
                .targetId(l.getTargetId())
                .ipAddress(l.getIpAddress())
                .userAgent(l.getUserAgent())
                .createdAt(l.getCreatedAt())
                .build());
    }

    private void recordAuditLog(String action, String targetEntity, UUID targetId, String before, String after) {
        try {
            UUID actorId = SecurityUtils.getCurrentUserId();
            systemAuditLogRepository.save(SystemAuditLog.builder()
                    .actorId(actorId)
                    .action(action)
                    .targetEntity(targetEntity)
                    .targetId(targetId)
                    .payloadBefore(before != null ? "\"" + before + "\"" : null)
                    .payloadAfter(after != null ? "\"" + after + "\"" : null)
                    .ipAddress("127.0.0.1")
                    .userAgent("Internal Admin Console")
                    .build());
        } catch (Exception e) {
            log.warn("Failed to record system audit log: {}", e.getMessage());
        }
    }

    private AdminReportResponse mapToReportResponse(Report r) {
        return AdminReportResponse.builder()
                .id(r.getId())
                .reporterId(r.getReporterId())
                .targetType(r.getTargetType())
                .targetId(r.getTargetId())
                .reportType(r.getReportType())
                .detail(r.getDetail())
                .evidenceImages(r.getEvidenceImages())
                .status(r.getStatus().name())
                .severity(r.getSeverity().name())
                .handlerId(r.getHandlerId())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
