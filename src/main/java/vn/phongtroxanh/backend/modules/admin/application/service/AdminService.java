package vn.phongtroxanh.backend.modules.admin.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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
import vn.phongtroxanh.backend.modules.rental.domain.Rental;
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

import org.springframework.security.crypto.password.PasswordEncoder;
import vn.phongtroxanh.backend.common.mail.EmailNotificationPort;
import vn.phongtroxanh.backend.modules.notification.application.service.NotificationService;
import vn.phongtroxanh.backend.modules.notification.domain.NotificationType;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final EntityManager entityManager;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final EmailNotificationPort emailNotificationPort;

    public AdminDashboardResponse getDashboard() {
        List<User> allUsers = userRepository.findAll();
        long totalUsers = allUsers.size();
        long totalLandlords = allUsers.stream().filter(u -> u.getRole() == UserRole.LANDLORD).count();
        long totalTenants = allUsers.stream().filter(u -> u.getRole() == UserRole.TENANT).count();
        long totalRooms = roomRepository.count();
        long activeRooms = roomRepository.findAll().stream().filter(r -> r.getStatus() == RoomStatus.AVAILABLE).count();
        long totalMatches = matchRepository.count();
        long totalRentals = rentalRepository.count();
        long activeRentals = rentalRepository.findAll().stream().filter(r -> r.getStatus() == RentalStatus.CHECKED_IN).count();
        long pendingKyc = userVerificationRepository.findByStatus(VerificationStatus.PENDING, Pageable.unpaged()).getTotalElements();
        long pendingDisputes = reviewDisputeRepository.findByStatus("PENDING_REVIEW", Pageable.unpaged()).getTotalElements();

        List<PaymentTransaction> successfulTx = paymentTransactionRepository.findAll().stream()
                .filter(t -> t.getStatus() == PaymentStatus.SUCCESS)
                .toList();

        BigDecimal totalRevenue = successfulTx.stream()
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Review> allReviews = reviewRepository.findAll();
        long totalReviews = allReviews.size();
        double averageRating = allReviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(4.9);
        averageRating = Math.round(averageRating * 10.0) / 10.0;
        long verifiedUsersCount = allUsers.stream()
                .filter(u -> Boolean.TRUE.equals(u.getIsVerified()))
                .count();

        // 1. Phân bố điểm uy tín TrustScore thật
        long c0_49 = allUsers.stream().filter(u -> u.getTrustScore() < 50).count();
        long c50_69 = allUsers.stream().filter(u -> u.getTrustScore() >= 50 && u.getTrustScore() < 70).count();
        long c70_84 = allUsers.stream().filter(u -> u.getTrustScore() >= 70 && u.getTrustScore() < 85).count();
        long c85_100 = allUsers.stream().filter(u -> u.getTrustScore() >= 85).count();
        List<AdminDashboardResponse.TrustScoreRangeDTO> trustScoreDistribution = List.of(
                new AdminDashboardResponse.TrustScoreRangeDTO("Dưới 50 (Cần cải thiện)", c0_49, totalUsers > 0 ? Math.round(c0_49 * 100.0 / totalUsers) : 0),
                new AdminDashboardResponse.TrustScoreRangeDTO("50 - 69 (Tiêu chuẩn)", c50_69, totalUsers > 0 ? Math.round(c50_69 * 100.0 / totalUsers) : 0),
                new AdminDashboardResponse.TrustScoreRangeDTO("70 - 84 (Uy tín cao)", c70_84, totalUsers > 0 ? Math.round(c70_84 * 100.0 / totalUsers) : 0),
                new AdminDashboardResponse.TrustScoreRangeDTO("85 - 100 (Xuất sắc)", c85_100, totalUsers > 0 ? Math.round(c85_100 * 100.0 / totalUsers) : 0)
        );

        // 2. Thống kê tăng trưởng 6 tháng gần nhất từ DB
        List<AdminDashboardResponse.MonthlyStatDTO> monthlyStats = new ArrayList<>();
        java.time.YearMonth currentYm = java.time.YearMonth.now();
        List<Rental> allRentals = rentalRepository.findAll();
        for (int i = 5; i >= 0; i--) {
            java.time.YearMonth ym = currentYm.minusMonths(i);
            Instant start = ym.atDay(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
            Instant end = ym.plusMonths(1).atDay(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();

            long newUsers = allUsers.stream().filter(u -> u.getCreatedAt() != null && !u.getCreatedAt().isBefore(start) && u.getCreatedAt().isBefore(end)).count();
            long newRentals = allRentals.stream().filter(r -> r.getCreatedAt() != null && !r.getCreatedAt().isBefore(start) && r.getCreatedAt().isBefore(end)).count();
            BigDecimal rev = successfulTx.stream()
                    .filter(t -> t.getCreatedAt() != null && !t.getCreatedAt().isBefore(start) && t.getCreatedAt().isBefore(end))
                    .map(PaymentTransaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            monthlyStats.add(new AdminDashboardResponse.MonthlyStatDTO("T" + ym.getMonthValue(), newUsers, newRentals, rev));
        }

        // 3. Phân bố sao đánh giá thật
        List<AdminDashboardResponse.RatingBreakdownDTO> ratingDistribution = new ArrayList<>();
        for (int star = 5; star >= 1; star--) {
            int finalStar = star;
            long count = allReviews.stream().filter(r -> r.getRating() != null && r.getRating() == finalStar).count();
            double pct = totalReviews > 0 ? Math.round(count * 100.0 / totalReviews) : 0;
            ratingDistribution.add(new AdminDashboardResponse.RatingBreakdownDTO(star, count, pct));
        }

        // 4. Danh sách đánh giá gần đây nhất từ DB
        List<AdminDashboardResponse.RecentReviewDTO> recentReviews = allReviews.stream()
                .sorted((a, b) -> (b.getCreatedAt() != null ? b.getCreatedAt() : Instant.MIN)
                        .compareTo(a.getCreatedAt() != null ? a.getCreatedAt() : Instant.MIN))
                .limit(4)
                .map(r -> {
                    User reviewer = userRepository.findById(r.getReviewerId()).orElse(null);
                    String name = reviewer != null ? reviewer.getFullName() : "Người dùng Phòng Trọ Xanh";
                    String roleStr = (reviewer != null && reviewer.getRole() == UserRole.LANDLORD) ? "Chủ trọ" : "Người thuê";
                    String rTitle = "Phòng trọ cao cấp";
                    if (r.getRoomId() != null) {
                        Room room = roomRepository.findById(r.getRoomId()).orElse(null);
                        if (room != null && room.getTitle() != null) {
                            rTitle = room.getTitle();
                        }
                    }
                    return AdminDashboardResponse.RecentReviewDTO.builder()
                            .id(r.getId())
                            .reviewerName(name)
                            .reviewerRole(roleStr)
                            .rating(r.getRating() != null ? r.getRating() : 5)
                            .comment(r.getComment())
                            .roomTitle(rTitle)
                            .createdAt(r.getCreatedAt())
                            .build();
                })
                .toList();

        // 5. Thống kê doanh thu theo gói dịch vụ
        Map<String, List<PaymentTransaction>> byPlan = successfulTx.stream()
                .collect(java.util.stream.Collectors.groupingBy(t -> t.getItemName() != null ? t.getItemName() : "Gói dịch vụ"));
        List<AdminDashboardResponse.PackageRevenueDTO> packageStats = new ArrayList<>();
        byPlan.forEach((planName, txList) -> {
            BigDecimal sum = txList.stream().map(PaymentTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            packageStats.add(new AdminDashboardResponse.PackageRevenueDTO(planName, planName, sum, (long) txList.size()));
        });

        List<Room> allRoomsList = roomRepository.findAll();
        // 6. Phân bố loại phòng thực tế từ DB
        Map<String, Long> roomTypeCounts = allRoomsList.stream()
                .filter(r -> r.getRoomType() != null)
                .collect(java.util.stream.Collectors.groupingBy(r -> {
                    String t = r.getRoomType();
                    if ("PHONG_KHEP_KIN".equalsIgnoreCase(t)) return "Phòng khép kín";
                    if ("KTX_SLEEPBOX".equalsIgnoreCase(t) || "KTX".equalsIgnoreCase(t)) return "KTX / Sleepbox";
                    if ("CAN_HO_MINI".equalsIgnoreCase(t) || "CHUNG_CU_MINI".equalsIgnoreCase(t)) return "Căn hộ mini";
                    if ("NHA_NGUYEN_CAN".equalsIgnoreCase(t)) return "Nhà nguyên căn";
                    if ("PHONG_OGHEP".equalsIgnoreCase(t)) return "Phòng ở ghép";
                    if ("PHONG_TRO".equalsIgnoreCase(t)) return "Phòng trọ truyền thống";
                    return t;
                }, java.util.stream.Collectors.counting()));

        List<AdminDashboardResponse.CategoryStatDTO> roomTypeDistribution = new ArrayList<>();
        roomTypeCounts.forEach((name, count) -> {
            double pct = totalRooms > 0 ? Math.round(count * 100.0 / totalRooms) : 0;
            roomTypeDistribution.add(new AdminDashboardResponse.CategoryStatDTO(name, count, pct));
        });
        roomTypeDistribution.sort((a, b) -> Long.compare(b.getCount(), a.getCount()));

        // 7. Phân bố phòng theo Quận/Huyện thực tế từ DB
        Map<String, Long> districtCounts = allRoomsList.stream()
                .filter(r -> r.getDistrict() != null && !r.getDistrict().isBlank())
                .collect(java.util.stream.Collectors.groupingBy(Room::getDistrict, java.util.stream.Collectors.counting()));

        List<AdminDashboardResponse.CategoryStatDTO> districtDistribution = new ArrayList<>();
        districtCounts.forEach((name, count) -> {
            double pct = totalRooms > 0 ? Math.round(count * 100.0 / totalRooms) : 0;
            districtDistribution.add(new AdminDashboardResponse.CategoryStatDTO(name, count, pct));
        });
        districtDistribution.sort((a, b) -> Long.compare(b.getCount(), a.getCount()));
        List<AdminDashboardResponse.CategoryStatDTO> topDistricts = districtDistribution.stream().limit(6).toList();

        // 8. Phân bố trạng thái phòng
        long stAvailable = allRoomsList.stream().filter(r -> r.getStatus() == RoomStatus.AVAILABLE).count();
        long stRented = allRoomsList.stream().filter(r -> r.getStatus() == RoomStatus.RENTED).count();
        long stHidden = totalRooms - stAvailable - stRented;
        List<AdminDashboardResponse.CategoryStatDTO> roomStatusDistribution = List.of(
                new AdminDashboardResponse.CategoryStatDTO("Sẵn sàng cho thuê (Available)", stAvailable, totalRooms > 0 ? Math.round(stAvailable * 100.0 / totalRooms) : 0),
                new AdminDashboardResponse.CategoryStatDTO("Đang có người thuê (Rented)", stRented, totalRooms > 0 ? Math.round(stRented * 100.0 / totalRooms) : 0),
                new AdminDashboardResponse.CategoryStatDTO("Tạm ẩn / Đang bảo trì", stHidden, totalRooms > 0 ? Math.round(stHidden * 100.0 / totalRooms) : 0)
        );

        // 9. Thống kê duyệt CCCD toàn hệ thống
        List<UserVerification> allVerifications = userVerificationRepository.findAll();
        long kycPending = allVerifications.stream().filter(v -> v.getStatus() == VerificationStatus.PENDING).count();
        long kycApproved = allVerifications.stream().filter(v -> v.getStatus() == VerificationStatus.APPROVED).count();
        long kycRejected = allVerifications.stream().filter(v -> v.getStatus() == VerificationStatus.REJECTED).count();
        double approvalRate = (kycApproved + kycRejected) > 0 ? Math.round(kycApproved * 100.0 / (kycApproved + kycRejected)) : 100.0;
        AdminDashboardResponse.KycOverviewDTO kycOverview = AdminDashboardResponse.KycOverviewDTO.builder()
                .pendingCount(kycPending)
                .approvedCount(kycApproved)
                .rejectedCount(kycRejected)
                .approvalRate(approvalRate)
                .build();

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
                .totalReviews(totalReviews)
                .averageRating(averageRating)
                .verifiedUsersCount(verifiedUsersCount)
                .trustScoreDistribution(trustScoreDistribution)
                .monthlyStats(monthlyStats)
                .ratingDistribution(ratingDistribution)
                .recentReviews(recentReviews)
                .packageStats(packageStats)
                .roomTypeDistribution(roomTypeDistribution)
                .districtDistribution(topDistricts)
                .roomStatusDistribution(roomStatusDistribution)
                .kycOverview(kycOverview)
                .build();
    }

    public Page<KycAuditItemDTO> getPendingKycPage(String search, UserRole role, int page, int limit) {
        List<UserVerification> list = userVerificationRepository.findByStatusOrderByCreatedAtAsc(VerificationStatus.PENDING, Pageable.unpaged()).getContent();
        List<KycAuditItemDTO> filtered = new ArrayList<>();
        String searchLower = (search != null && !search.isBlank()) ? search.trim().toLowerCase() : null;

        for (UserVerification v : list) {
            User user = userRepository.findById(v.getUserId()).orElse(null);
            if (role != null && (user == null || user.getRole() != role)) {
                continue;
            }

            String name = user != null ? user.getFullName() : "";
            String email = user != null ? user.getEmail() : "";
            String phone = user != null && user.getPhoneNumber() != null ? user.getPhoneNumber() : "";
            String idCard = v.getIdCardNumber() != null ? v.getIdCardNumber() : "";

            if (searchLower != null) {
                boolean match = (name != null && name.toLowerCase().contains(searchLower))
                        || (email != null && email.toLowerCase().contains(searchLower))
                        || phone.contains(searchLower)
                        || idCard.contains(searchLower);
                if (!match) continue;
            }

            filtered.add(KycAuditItemDTO.builder()
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

        int pageSize = limit > 0 ? limit : 10;
        int total = filtered.size();
        int start = Math.min(page * pageSize, total);
        int end = Math.min(start + pageSize, total);
        List<KycAuditItemDTO> pagedList = filtered.subList(start, end);

        return new PageImpl<>(pagedList, PageRequest.of(page, pageSize), total);
    }

    public List<KycAuditItemDTO> getPendingKycList() {
        return getPendingKycPage(null, null, 0, 1000).getContent();
    }

    @Transactional
    public void approveKyc(UUID verificationId) {
        UserVerification verification = userVerificationRepository.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("VERIFICATION_NOT_FOUND", "Không tìm thấy hồ sơ xác thực"));

        entityManager.refresh(verification, LockModeType.PESSIMISTIC_WRITE);
        if (verification.getStatus() != VerificationStatus.PENDING)
            throw new BadRequestException("KYC_ALREADY_REVIEWED", "Hồ sơ xác thực đã được xử lý");

        verification.setStatus(VerificationStatus.APPROVED);
        verification.setReviewedBy(SecurityUtils.getCurrentUserId());
        verification.setReviewedAt(Instant.now());
        userVerificationRepository.save(verification);

        // Update User TrustScore (+30 points) and mark verified
        userRepository.findById(verification.getUserId()).ifPresent(user -> {
            entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
            if (Boolean.TRUE.equals(user.getIsVerified())) return;
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

        entityManager.refresh(verification, LockModeType.PESSIMISTIC_WRITE);
        if (verification.getStatus() != VerificationStatus.PENDING)
            throw new BadRequestException("KYC_ALREADY_REVIEWED", "Hồ sơ xác thực đã được xử lý");

        verification.setStatus(VerificationStatus.REJECTED);
        verification.setReviewedBy(SecurityUtils.getCurrentUserId());
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

    @Transactional
    public void verifyUser(UUID userId, boolean verified) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy người dùng"));

        user.setIsVerified(verified);
        if (verified) {
            user.setTrustScore(Math.min(100, user.getTrustScore() + 30));
        }
        userRepository.save(user);

        recordAuditLog(verified ? "GRANT_USER_VERIFIED" : "REVOKE_USER_VERIFIED", "USER", userId, null,
                verified ? "Cấp tích xanh xác minh thủ công" : "Thu hồi tích xanh xác minh");

        notificationService.create(userId,
                verified ? "Tài khoản của bạn đã được xác minh" : "Trạng thái xác minh đã thay đổi",
                verified ? "Ban quản trị đã cấp tích xanh xác minh cho hồ sơ của bạn. Điểm uy tín +30." : "Ban quản trị đã thu hồi trạng thái xác minh của hồ sơ.",
                NotificationType.SYSTEM,
                Map.of("isVerified", verified));
    }

    @Transactional
    public String adminResetPassword(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy người dùng"));

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BadRequestException("USER_NO_EMAIL", "Người dùng không có địa chỉ email hợp lệ để nhận mật khẩu");
        }

        String chars = "0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder("Ptx@");
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        String tempPassword = sb.toString();

        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        userRepository.save(user);

        // Gửi email chứa mật khẩu mới an toàn trực tiếp tới hòm thư người dùng (Zero-knowledge for Admin)
        emailNotificationPort.sendAdminResetPasswordEmail(user.getEmail(), tempPassword);

        String maskedEmail = maskEmail(user.getEmail());

        recordAuditLog("ADMIN_RESET_PASSWORD", "USER", userId, null,
                "Quản trị viên đặt lại mật khẩu và gửi trực tiếp qua email tới " + maskedEmail);

        notificationService.create(userId,
                "Mật khẩu tài khoản đã được đặt lại",
                "Mật khẩu tài khoản của bạn đã được đặt lại bởi Ban Quản Trị và gửi an toàn đến email " + maskedEmail + ". Vui lòng kiểm tra hộp thư và đổi mật khẩu mới ngay sau khi đăng nhập.",
                NotificationType.SYSTEM,
                Map.of("action", "RESET_PASSWORD"));

        log.info("[AdminService] Password reset successfully for user {} and emailed directly to {}", userId, maskedEmail);
        return maskedEmail;
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email != null ? email : "";
        int atIndex = email.indexOf('@');
        String name = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (name.length() <= 2) {
            return name.charAt(0) + "***" + domain;
        }
        return name.substring(0, 2) + "***" + domain;
    }

    @Transactional
    public void sendUserNotification(UUID userId, String title, String message) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy người dùng"));

        String safeTitle = (title != null && !title.isBlank()) ? title : "Thông báo từ Ban Quản Trị";

        notificationService.create(userId,
                safeTitle,
                message,
                NotificationType.SYSTEM,
                Map.of("fromAdmin", true));

        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            try {
                emailNotificationPort.sendNotificationEmail(user.getEmail(), safeTitle, message);
            } catch (Exception e) {
                log.warn("[AdminService] Could not send notification email to {}: {}", user.getEmail(), e.getMessage());
            }
        }

        recordAuditLog("ADMIN_SEND_NOTIFICATION", "USER", userId, null, "Gửi thông báo: " + message);
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

        entityManager.refresh(dispute, LockModeType.PESSIMISTIC_WRITE);
        if (!"PENDING_REVIEW".equals(dispute.getStatus()))
            throw new BadRequestException("DISPUTE_ALREADY_RESOLVED", "Khiếu nại đã được xử lý");
        if (request.getDecision() != ReviewDisputeStatus.RESOLVED_REMOVED && request.getDecision() != ReviewDisputeStatus.RESOLVED_UPHELD)
            throw new BadRequestException("INVALID_DISPUTE_DECISION", "Quyết định xử lý khiếu nại không hợp lệ");

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
            if (review.getRating() <= 2) userRepository.findById(review.getRevieweeId()).ifPresent(u -> {
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

    @Transactional
    public AdminReportResponse createReport(CreateReportRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        Report report = Report.builder()
                .reporterId(currentUserId)
                .targetType(request.getTargetType().toUpperCase())
                .targetId(request.getTargetId())
                .reportType(request.getReportType())
                .detail(request.getDetail())
                .evidenceImages(request.getEvidenceImages())
                .status(ReportStatus.NEW)
                .severity(request.getSeverity() != null ? request.getSeverity() : ReportSeverity.MEDIUM)
                .build();

        report = reportRepository.save(report);
        log.info("User {} submitted report {} for {}/{}", currentUserId, report.getId(), report.getTargetType(), report.getTargetId());
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
